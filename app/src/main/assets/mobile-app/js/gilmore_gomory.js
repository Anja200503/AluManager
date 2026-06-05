'use strict';

/* ════════════════════════════════════════════════════════════
   ALGORITHME DE GILMORE-GOMORY — 1D Cutting Stock Problem
   Référence : Gilmore & Gomory, Operations Research, 1961/1963

   Méthode : Génération de colonnes (Column Generation)

   Étapes :
     1. Patrons initiaux : un par type de pièce (barre remplie au max)
     2. Résolution LP du problème maître → prix duaux π
     3. Sac-à-dos DP (pricing problem) → nouveau patron candidat
     4. Si Σ π_i × a_i > 1 + ε → coût réduit négatif → ajouter patron
     5. Répéter jusqu'à optimum LP (solution fractionnaire)
     6. Arrondi floor(x_j) + complétion BFD → solution entière
════════════════════════════════════════════════════════════ */

const GG = (() => {

  /* ══════════════════════════════════════════════════════════
     SOLVEUR SIMPLEX (problème maître)
     Résout :  min  c^T x
               s.t. Ax ≥ b,  x ≥ 0
     Retourne  { x: solution primale, duals: prix duaux }

     Implémentation : Simplex tableau, méthode du Grand-M
     Variables : [x (patrons) | s (surplus) | a (artificiels)]
  ══════════════════════════════════════════════════════════ */
  function _simplex(c, A, b) {
    const m  = A.length;      // nb contraintes (types de pièces)
    const n  = c.length;      // nb variables   (patrons courants)
    const M  = 1e8;           // pénalité Big-M pour les artificiels
    const nv = n + 2 * m;     // total colonnes

    // ── Tableau : [A | -I_surplus | +I_artificiel | b] ──
    const T = [];
    for (let i = 0; i < m; i++) {
      const r = new Float64Array(nv + 1);
      for (let j = 0; j < n; j++) r[j] = A[i][j]; // coefficients A
      r[n + i]     = -1;              // colonne surplus  −sᵢ
      r[n + m + i] =  1;              // colonne artificiel +aᵢ
      r[nv]        = Math.max(0, b[i]); // second membre
      T.push(r);
    }
    // Ligne objectif : min Σ cⱼxⱼ + M·Σaᵢ
    const obj = new Float64Array(nv + 1);
    for (let j = 0;      j < n;  j++) obj[j] = c[j];
    for (let j = n + m; j < nv; j++) obj[j] = M;
    T.push(obj);

    // Base initiale = variables artificielles
    const bas = Array.from({ length: m }, (_, i) => n + m + i);

    // Éliminer les artificiels de la ligne objectif (coût réduit nul pour la base)
    for (let i = 0; i < m; i++) {
      const f = T[m][bas[i]];
      if (f !== 0) for (let j = 0; j <= nv; j++) T[m][j] -= f * T[i][j];
    }

    // ── Itérations du Simplex ──
    for (let it = 0; it < 8000; it++) {
      // Variable entrante : coût réduit le plus négatif (règle de Dantzig)
      let ec = -1, ev = -1e-8;
      for (let j = 0; j < nv; j++) if (T[m][j] < ev) { ev = T[m][j]; ec = j; }
      if (ec < 0) break; // ✓ Optimal

      // Variable sortante : test du rapport minimum (anti-dégénérescence de Bland)
      let lr = -1, lv = Infinity;
      for (let i = 0; i < m; i++) {
        if (T[i][ec] > 1e-9) {
          const r = T[i][nv] / T[i][ec];
          if (r < lv - 1e-10 ||
             (Math.abs(r - lv) < 1e-10 && bas[i] < (lr >= 0 ? bas[lr] : Infinity))) {
            lv = r; lr = i;
          }
        }
      }
      if (lr < 0) break; // Non borné (ne doit pas arriver ici)

      // ── Opération de pivot ──
      const pv = T[lr][ec];
      for (let j = 0; j <= nv; j++) T[lr][j] /= pv;
      for (let i = 0; i <= m; i++) {
        if (i === lr) continue;
        const f = T[i][ec];
        if (Math.abs(f) > 1e-12) for (let j = 0; j <= nv; j++) T[i][j] -= f * T[lr][j];
      }
      bas[lr] = ec;
    }

    // ── Extraction de la solution primale ──
    const x = new Float64Array(n);
    for (let i = 0; i < m; i++) if (bas[i] < n) x[bas[i]] = Math.max(0, T[i][nv]);

    // ── Extraction des prix duaux (shadow prices) ──
    // Pour une contrainte ≥ : πᵢ = coût réduit de la variable surplus sᵢ = T[m][n+i]
    // (positif à l'optimum — complémentarité)
    const duals = new Float64Array(m);
    for (let i = 0; i < m; i++) duals[i] = Math.max(0, T[m][n + i]);

    return { x, duals };
  }


  /* ══════════════════════════════════════════════════════════
     SAC-À-DOS ILLIMITÉ (pricing problem)
     Maximise : Σ πᵢ × aᵢ
     Sous     : Σ (lᵢ + k) × aᵢ ≤ L + k   (dernier trait gratuit)
                aᵢ ≥ 0, entier

     Si valeur > 1 + ε → nouveau patron à coût réduit négatif → l'ajouter
     Sinon             → solution LP déjà optimale
  ══════════════════════════════════════════════════════════ */
  function _knapsack(lengths, duals, L, k) {
    const n   = lengths.length;
    const cap = L + k; // +k : le dernier trait de scie n'est pas nécessaire

    const dp   = new Float64Array(cap + 1); // dp[w] = valeur max avec capacité w
    const from = new Int32Array(cap + 1).fill(-1); // pour reconstruire le patron

    for (let w = 1; w <= cap; w++) {
      for (let i = 0; i < n; i++) {
        if (duals[i] < 1e-9) continue;      // type sans valeur → ignorer
        const cost = lengths[i] + k;
        if (cost > w) continue;
        const val = duals[i] + dp[w - cost];
        if (val > dp[w] + 1e-9) { dp[w] = val; from[w] = i; }
      }
    }

    // ── Reconstruction du patron ──
    const pat = new Array(n).fill(0);
    let w = cap;
    while (w > 0 && from[w] >= 0) { const i = from[w]; pat[i]++; w -= lengths[i] + k; }

    return { value: dp[cap], pat };
  }


  /* ══════════════════════════════════════════════════════════
     GÉNÉRATION DE COLONNES — cœur de Gilmore-Gomory
  ══════════════════════════════════════════════════════════ */
  function _columnGen(types, L, k) {
    const n   = types.length;
    const len = types.map(t => t.length); // longueurs des types
    const dem = types.map(t => t.qty);    // demandes

    // ── Patrons initiaux : un par type (barre remplie au maximum) ──
    const pats = len.map((li, i) => {
      const p = new Array(n).fill(0);
      p[i] = Math.max(1, Math.floor((L + k) / (li + k)));
      return p;
    });

    // ── Boucle de génération ──
    for (let it = 0; it < 500; it++) {
      const np = pats.length;
      // Matrice A : A[i][j] = nb pièces de type i dans le patron j
      const A  = dem.map((_, i) => pats.map(p => p[i]));
      const { duals } = _simplex(new Array(np).fill(1), A, dem);

      // Pricing problem : trouver le meilleur nouveau patron
      const { value, pat } = _knapsack(len, duals, L, k);

      // Critère de Gilmore-Gomory : valeur > 1 + ε → coût réduit = 1 - valeur < 0
      if (value <= 1 + 1e-6) break; // ✓ Optimum LP atteint
      pats.push(pat);
    }

    return pats;
  }


  /* ══════════════════════════════════════════════════════════
     CONSTRUCTION DU PLAN DE COUPE ENTIER
     Phase 1 : appliquer floor(x_j) fois chaque patron (LP arrondi)
     Phase 2 : compléter les pièces restantes par Best-Fit Decreasing
  ══════════════════════════════════════════════════════════ */
  function _buildPlan(pats, types, L, k) {
    const n    = types.length;
    const len  = types.map(t => t.length);
    const np   = pats.length;

    // LP final pour obtenir x_j (solution fractionnaire)
    const A   = types.map((_, i) => pats.map(p => p[i]));
    const dem = types.map(t => t.qty);
    const { x } = _simplex(new Array(np).fill(1), A, dem);

    const rem  = [...dem]; // demande restante
    const bars = [];

    // ── Phase 1 : floor(x_j) fois chaque patron ──
    const order = Array.from({ length: np }, (_, j) => j).sort((a, b) => x[b] - x[a]);
    for (const j of order) {
      const times = Math.floor(x[j] + 1e-6); // +ε pour les quasi-entiers
      for (let t = 0; t < times; t++) {
        const bar = _applyPat(pats[j], len, rem, L, k);
        if (bar) bars.push(bar);
      }
    }

    // ── Phase 2 : Best-Fit Decreasing pour les pièces restantes ──
    // Traiter les types par longueur décroissante
    const typeOrder = Array.from({ length: n }, (_, i) => i).sort((a, b) => len[b] - len[a]);
    for (const i of typeOrder) {
      while (rem[i] > 0) {
        // Chercher la barre avec l'espace le plus petit qui peut accueillir la pièce
        let bestBar = null, bestWaste = Infinity;
        for (const bar of bars) {
          const aw = bar.waste - len[i] - k; // espace après placement
          if (aw >= -k + 1e-6 && aw < bestWaste) { bestWaste = aw; bestBar = bar; }
        }
        if (bestBar) {
          bestBar.cuts.push({ length: len[i], typeIdx: i });
          bestBar.waste -= len[i] + k;
          rem[i]--;
        } else {
          // Nouvelle barre
          bars.push({ cuts: [{ length: len[i], typeIdx: i }], waste: L - len[i] - k });
          rem[i]--;
        }
      }
    }

    // Corriger le dernier trait : chaque barre a un trait de scie en trop
    for (const bar of bars) {
      bar.realWaste = Math.max(0, bar.waste + k);
    }

    return bars;
  }

  function _applyPat(pat, len, rem, L, k) {
    const bar = { cuts: [], waste: L };
    for (let i = 0; i < len.length; i++) {
      const use = Math.min(pat[i], rem[i]);
      for (let q = 0; q < use; q++) {
        bar.cuts.push({ length: len[i], typeIdx: i });
        bar.waste -= len[i] + k;
        rem[i]--;
      }
    }
    return bar.cuts.length ? bar : null;
  }


  /* ══════════════════════════════════════════════════════════
     API PUBLIQUE
  ══════════════════════════════════════════════════════════ */

  /**
   * Résout le problème de coupe par la méthode de Gilmore-Gomory.
   *
   * @param {Array<{length:number, qty:number, label:string}>} pieces
   *   length : longueur de la pièce en mm
   *   qty    : quantité requise (défaut 1)
   *   label  : étiquette affichée
   * @param {number} barLength  Longueur d'une barre en mm (défaut 6000)
   * @param {number} kerf       Largeur du trait de scie en mm (défaut 3)
   * @returns {{ bars, stats, patterns }}
   */
  function solve(pieces, barLength = 6000, kerf = 3) {
    if (!pieces || !pieces.length) return _empty();

    // ── Validation ──
    for (const p of pieces) {
      if (!p.length || p.length <= 0) throw new Error(`Longueur invalide : ${p.length}`);
      if (p.length > barLength) throw new Error(`Pièce ${p.length}mm dépasse la barre ${barLength}mm`);
    }

    // ── Agrégation par longueur ──
    const map = new Map();
    for (const p of pieces) {
      const L = Math.round(p.length);
      if (!map.has(L)) map.set(L, { length: L, qty: 0, labels: [] });
      const t = map.get(L);
      t.qty += Math.max(1, parseInt(p.qty) || 1);
      if (p.label) t.labels.push(p.label);
    }
    const types = [...map.values()].sort((a, b) => b.length - a.length);

    // ── Gilmore-Gomory : génération de colonnes ──
    const pats = _columnGen(types, barLength, kerf);

    // ── Construction du plan entier ──
    const bars = _buildPlan(pats, types, barLength, kerf);

    // ── Attribution des étiquettes et couleurs ──
    const pools = types.map(t => [...t.labels]);
    const COLORS = [
      '#2980b9','#27ae60','#8e44ad','#d35400','#16a085',
      '#c0392b','#2c3e50','#f39c12','#1abc9c','#e74c3c',
    ];
    for (const bar of bars) {
      for (const cut of bar.cuts) {
        cut.label     = pools[cut.typeIdx].shift() || `${cut.length}mm`;
        cut.typeColor = COLORS[cut.typeIdx % COLORS.length];
      }
    }

    // ── Statistiques ──
    const totalWaste = bars.reduce((s, b) => s + (b.realWaste ?? b.waste), 0);
    const eff        = 100 - totalWaste / (bars.length * barLength) * 100;

    return {
      bars,
      stats: {
        nbBarres:      bars.length,
        nbPatrons:     pats.length,
        nbPieces:      bars.reduce((s, b) => s + b.cuts.length, 0),
        totalWaste:    Math.round(totalWaste),
        pctChute:      (100 - eff).toFixed(1),
        pctEfficacite: eff.toFixed(1),
        barLength,
        kerf,
      },
    };
  }

  function _empty() {
    return {
      bars: [], stats: {
        nbBarres: 0, nbPatrons: 0, nbPieces: 0,
        totalWaste: 0, pctChute: '0.0', pctEfficacite: '0.0',
      },
    };
  }


  /* ══════════════════════════════════════════════════════════
     RENDU VISUEL
  ══════════════════════════════════════════════════════════ */

  /**
   * Affiche le plan de coupe dans la modal #ggOverlay.
   * @param {object} commande  Objet commande avec .produits[].dimensions[]
   * @param {number} barLength Longueur barre en mm
   * @param {number} kerf      Trait de scie en mm
   */
  function openModal(commande, barLength = 6000, kerf = 3) {
    const overlay = document.getElementById('ggOverlay');
    const body    = document.getElementById('ggBody');
    if (!overlay || !body) return;

    // ── Extraction des pièces depuis la commande ──
    const pieces = [];
    for (const prod of commande.produits || []) {
      const type = _shortType(prod.type_produit || 'pièce');
      for (const dim of prod.dimensions || []) {
        const qty = parseInt(dim.qty) || 1;
        // Dimensions en cm → mm
        const h = Math.round(parseFloat(dim.h || 0) * 10);
        const l = Math.round(parseFloat(dim.l || 0) * 10);
        if (h > 10) pieces.push({ length: h, qty, label: `${type} H${dim.h}cm` });
        if (l > 10) pieces.push({ length: l, qty, label: `${type} L${dim.l}cm` });
      }
    }

    if (!pieces.length) {
      body.innerHTML = `<div class="gg-empty">⚠️ Aucune dimension trouvée dans cette commande.</div>`;
      overlay.classList.add('active');
      return;
    }

    // ── Résoudre ──
    body.innerHTML = `<div class="gg-loading"><div class="gg-spinner"></div>Calcul Gilmore-Gomory en cours…</div>`;
    overlay.classList.add('active');

    // Timeout pour laisser le DOM se mettre à jour avant le calcul
    setTimeout(() => {
      try {
        const result = GG.solve(pieces, barLength, kerf);
        body.innerHTML = _renderResult(result, commande, barLength);
      } catch (err) {
        body.innerHTML = `<div class="gg-empty">❌ Erreur : ${err.message}</div>`;
      }
    }, 50);
  }

  function closeModal() {
    const ov = document.getElementById('ggOverlay');
    if (ov) ov.classList.remove('active');
  }

  function _shortType(t) {
    const map = { fenetre:'Fen', porte:'Pte', lavarangana:'Lav', rideau:'Rid', vitrine:'Vit' };
    return map[t] || t.slice(0, 3);
  }

  function _renderResult(res, cmd, L) {
    const { bars, stats } = res;
    const ref    = cmd.reference || '';
    const client = cmd.client_nom || '';

    const statsHtml = `
      <div class="gg-stats-row">
        <div class="gg-stat"><span class="gg-sv">${stats.nbBarres}</span><span class="gg-sl">Barres</span></div>
        <div class="gg-stat"><span class="gg-sv">${stats.nbPieces}</span><span class="gg-sl">Pièces</span></div>
        <div class="gg-stat"><span class="gg-sv" style="color:#27ae60">${stats.pctEfficacite}%</span><span class="gg-sl">Efficacité</span></div>
        <div class="gg-stat"><span class="gg-sv" style="color:#e74c3c">${stats.pctChute}%</span><span class="gg-sl">Chute</span></div>
      </div>
      <div class="gg-meta">
        🔖 ${ref} · 👤 ${client} · 🪚 Trait ${stats.kerf}mm · Barre ${(L/10).toFixed(0)}cm
        · <b>${stats.nbPatrons} patron(s)</b> générés par Gilmore-Gomory
      </div>`;

    const barsHtml = bars.map((bar, idx) => _renderBar(bar, idx, L, stats.kerf)).join('');

    const legend = _renderLegend(bars);

    return statsHtml + `<div class="gg-bars-list">${barsHtml}</div>` + legend;
  }

  function _renderBar(bar, idx, L, k) {
    const waste = bar.realWaste ?? bar.waste;
    const wasteOk = waste < 50;

    const cuts = bar.cuts.map(c => {
      const pct = (c.length / L * 100).toFixed(3);
      return `<div class="gg-cut" style="width:${pct}%;background:${c.typeColor}"
                   title="${c.label} — ${c.length}mm">
                <span class="gg-cut-lbl">${c.length < 300 ? '' : c.length}</span>
              </div>
              <div class="gg-kerf" title="Trait ${k}mm"></div>`;
    }).join('');

    const wastePct = (waste / L * 100).toFixed(2);
    const wasteEl  = waste > 10
      ? `<div class="gg-waste" style="width:${wastePct}%;min-width:${waste < 60 ? 0 : 28}px"
              title="Chute ${waste}mm">
           ${waste >= 150 ? `<span>${waste}mm</span>` : ''}
         </div>`
      : '';

    return `
      <div class="gg-bar-wrap">
        <div class="gg-bar-label">
          <span class="gg-bar-num">Barre ${idx + 1}</span>
          <span class="gg-bar-info">${bar.cuts.length} pièce(s)</span>
          <span class="gg-bar-waste ${wasteOk ? 'ok' : ''}">${waste}mm chute</span>
        </div>
        <div class="gg-bar-visual">
          ${cuts}${wasteEl}
        </div>
      </div>`;
  }

  function _renderLegend(bars) {
    const seen = new Map();
    for (const bar of bars)
      for (const c of bar.cuts)
        if (!seen.has(c.typeIdx)) seen.set(c.typeIdx, { color: c.typeColor, label: c.label, length: c.length });

    const items = [...seen.values()].map(v =>
      `<span class="gg-leg-item">
         <span class="gg-leg-dot" style="background:${v.color}"></span>
         ${v.label}
       </span>`
    ).join('');

    return `<div class="gg-legend">${items}</div>`;
  }

  return { solve, openModal, closeModal };
})();
