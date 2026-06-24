package com.alumanager

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Probleme de coupe 1D (Cutting Stock) par la methode de Gilmore-Gomory
 * (generation de colonnes : simplex Big-M + sac-a-dos DP).
 * Port fidele de gilmore_gomory.js.
 */
object CuttingStock {

    data class Piece(val length: Int, val qty: Int, val label: String)
    data class Cut(val length: Int, val typeIdx: Int, var label: String = "", var color: Int = 0)
    class Bar(val cuts: MutableList<Cut> = mutableListOf(), var waste: Double = 0.0, var realWaste: Double = 0.0)
    data class Stats(
        val nbBarres: Int, val nbPatrons: Int, val nbPieces: Int,
        val totalWaste: Int, val pctChute: String, val pctEfficacite: String,
        val barLength: Int, val kerf: Int
    )
    data class Result(val bars: List<Bar>, val stats: Stats)

    private data class TypeAgg(val length: Int, var qty: Int, val labels: MutableList<String>)

    private val COLORS = intArrayOf(
        0xFF2980b9.toInt(), 0xFF27ae60.toInt(), 0xFF8e44ad.toInt(), 0xFFd35400.toInt(),
        0xFF16a085.toInt(), 0xFFc0392b.toInt(), 0xFF2c3e50.toInt(), 0xFFf39c12.toInt(),
        0xFF1abc9c.toInt(), 0xFFe74c3c.toInt()
    )

    /* ── Simplex Big-M : min cᵀx s.c. Ax ≥ b, x ≥ 0 → (x, duals) ── */
    private fun simplex(c: DoubleArray, A: Array<DoubleArray>, b: DoubleArray): Pair<DoubleArray, DoubleArray> {
        val m = A.size
        val n = c.size
        val bigM = 1e8
        val nv = n + 2 * m
        val T = Array(m + 1) { DoubleArray(nv + 1) }
        for (i in 0 until m) {
            for (j in 0 until n) T[i][j] = A[i][j]
            T[i][n + i] = -1.0
            T[i][n + m + i] = 1.0
            T[i][nv] = max(0.0, b[i])
        }
        for (j in 0 until n) T[m][j] = c[j]
        for (j in n + m until nv) T[m][j] = bigM
        val bas = IntArray(m) { n + m + it }
        for (i in 0 until m) {
            val f = T[m][bas[i]]
            if (f != 0.0) for (j in 0..nv) T[m][j] -= f * T[i][j]
        }
        var it = 0
        while (it < 8000) {
            var ec = -1; var ev = -1e-8
            for (j in 0 until nv) if (T[m][j] < ev) { ev = T[m][j]; ec = j }
            if (ec < 0) break
            var lr = -1; var lv = Double.POSITIVE_INFINITY
            for (i in 0 until m) {
                if (T[i][ec] > 1e-9) {
                    val r = T[i][nv] / T[i][ec]
                    if (r < lv - 1e-10 ||
                        (Math.abs(r - lv) < 1e-10 && bas[i] < (if (lr >= 0) bas[lr] else Int.MAX_VALUE))
                    ) { lv = r; lr = i }
                }
            }
            if (lr < 0) break
            val pv = T[lr][ec]
            for (j in 0..nv) T[lr][j] /= pv
            for (i in 0..m) {
                if (i == lr) continue
                val f = T[i][ec]
                if (Math.abs(f) > 1e-12) for (j in 0..nv) T[i][j] -= f * T[lr][j]
            }
            bas[lr] = ec
            it++
        }
        val x = DoubleArray(n)
        for (i in 0 until m) if (bas[i] < n) x[bas[i]] = max(0.0, T[i][nv])
        val duals = DoubleArray(m) { i -> max(0.0, T[m][n + i]) }
        return x to duals
    }

    /* ── Sac-a-dos illimite : max Σ duals_i a_i s.c. Σ(len_i+k) a_i ≤ L+k ── */
    private fun knapsack(lengths: IntArray, duals: DoubleArray, L: Int, k: Int): Pair<Double, IntArray> {
        val n = lengths.size
        val cap = L + k
        val dp = DoubleArray(cap + 1)
        val from = IntArray(cap + 1) { -1 }
        for (w in 1..cap) {
            for (i in 0 until n) {
                if (duals[i] < 1e-9) continue
                val cost = lengths[i] + k
                if (cost > w) continue
                val v = duals[i] + dp[w - cost]
                if (v > dp[w] + 1e-9) { dp[w] = v; from[w] = i }
            }
        }
        val pat = IntArray(n)
        var w = cap
        while (w > 0 && from[w] >= 0) { val i = from[w]; pat[i]++; w -= lengths[i] + k }
        return dp[cap] to pat
    }

    private fun columnGen(types: List<TypeAgg>, L: Int, k: Int): MutableList<IntArray> {
        val n = types.size
        val len = IntArray(n) { types[it].length }
        val dem = DoubleArray(n) { types[it].qty.toDouble() }
        val pats = MutableList(n) { i ->
            IntArray(n).also { it[i] = max(1, floor((L + k).toDouble() / (len[i] + k)).toInt()) }
        }
        var iter = 0
        while (iter < 500) {
            val np = pats.size
            val A = Array(n) { i -> DoubleArray(np) { j -> pats[j][i].toDouble() } }
            val (_, duals) = simplex(DoubleArray(np) { 1.0 }, A, dem)
            val (value, pat) = knapsack(len, duals, L, k)
            if (value <= 1 + 1e-6) break
            pats.add(pat)
            iter++
        }
        return pats
    }

    private fun applyPat(pat: IntArray, len: IntArray, rem: IntArray, L: Int, k: Int): Bar? {
        val bar = Bar(waste = L.toDouble())
        for (i in len.indices) {
            val use = min(pat[i], rem[i])
            for (q in 0 until use) {
                bar.cuts.add(Cut(len[i], i)); bar.waste -= (len[i] + k); rem[i]--
            }
        }
        return if (bar.cuts.isNotEmpty()) bar else null
    }

    private fun buildPlan(pats: List<IntArray>, types: List<TypeAgg>, L: Int, k: Int): MutableList<Bar> {
        val n = types.size
        val len = IntArray(n) { types[it].length }
        val np = pats.size
        val A = Array(n) { i -> DoubleArray(np) { j -> pats[j][i].toDouble() } }
        val dem = DoubleArray(n) { types[it].qty.toDouble() }
        val (x, _) = simplex(DoubleArray(np) { 1.0 }, A, dem)
        val rem = IntArray(n) { types[it].qty }
        val bars = mutableListOf<Bar>()
        val order = (0 until np).sortedByDescending { x[it] }
        for (j in order) {
            val times = floor(x[j] + 1e-6).toInt()
            for (t in 0 until times) applyPat(pats[j], len, rem, L, k)?.let { bars.add(it) }
        }
        val typeOrder = (0 until n).sortedByDescending { len[it] }
        for (i in typeOrder) {
            while (rem[i] > 0) {
                var bestBar: Bar? = null; var bestWaste = Double.POSITIVE_INFINITY
                for (bar in bars) {
                    val aw = bar.waste - len[i] - k
                    if (aw >= -k + 1e-6 && aw < bestWaste) { bestWaste = aw; bestBar = bar }
                }
                if (bestBar != null) {
                    bestBar.cuts.add(Cut(len[i], i)); bestBar.waste -= (len[i] + k); rem[i]--
                } else {
                    bars.add(Bar(mutableListOf(Cut(len[i], i)), (L - len[i] - k).toDouble())); rem[i]--
                }
            }
        }
        for (bar in bars) bar.realWaste = max(0.0, bar.waste + k)
        return bars
    }

    fun solve(pieces: List<Piece>, barLength: Int = 6000, kerf: Int = 3): Result {
        if (pieces.isEmpty()) return Result(emptyList(), Stats(0, 0, 0, 0, "0.0", "0.0", barLength, kerf))
        for (p in pieces) {
            require(p.length > 0) { "Longueur invalide : ${p.length}" }
            require(p.length <= barLength) { "Piece ${p.length}mm depasse la barre ${barLength}mm" }
        }
        val map = LinkedHashMap<Int, TypeAgg>()
        for (p in pieces) {
            val L = p.length
            val t = map.getOrPut(L) { TypeAgg(L, 0, mutableListOf()) }
            val q = max(1, p.qty)
            t.qty += q
            if (p.label.isNotEmpty()) repeat(q) { t.labels.add(p.label) }
        }
        val types = map.values.sortedByDescending { it.length }
        val pats = columnGen(types, barLength, kerf)
        val bars = buildPlan(pats, types, barLength, kerf)
        val pools = types.map { ArrayDeque(it.labels) }
        for (bar in bars) for (cut in bar.cuts) {
            cut.label = pools[cut.typeIdx].removeFirstOrNull() ?: "${cut.length}mm"
            cut.color = COLORS[cut.typeIdx % COLORS.size]
        }
        val totalWaste = bars.sumOf { it.realWaste }
        val eff = 100 - totalWaste / (bars.size.coerceAtLeast(1) * barLength) * 100
        return Result(
            bars,
            Stats(
                nbBarres = bars.size,
                nbPatrons = pats.size,
                nbPieces = bars.sumOf { it.cuts.size },
                totalWaste = Math.round(totalWaste).toInt(),
                pctChute = String.format("%.1f", 100 - eff),
                pctEfficacite = String.format("%.1f", eff),
                barLength = barLength, kerf = kerf
            )
        )
    }
}
