'use strict';

// ─── Initialisation Firestore ────────────────────────────────
const db = firebase.firestore();

// ─── État local ──────────────────────────────────────────────
let currentUser    = null;
let currentSociete = '';
let employes       = [];   // [{id, nom}]
let pointages      = {};   // { nomEmploye : 'present'|'absent'|'retard' }

// ─── Quand l'utilisateur est connecté ───────────────────────
firebase.auth().onAuthStateChanged(async user => {
  if (!user) return;
  currentUser    = user;
  currentSociete = user.photoURL || '';

  // Initialiser la page présence dès qu'elle est visible
  initPresencePage();
});

// ─── Initialisation page présence ───────────────────────────
function initPresencePage() {
  // Date du jour
  const now    = new Date();
  const jours  = ['Dimanche','Lundi','Mardi','Mercredi','Jeudi','Vendredi','Samedi'];
  const mois   = ['Janvier','Février','Mars','Avril','Mai','Juin',
                   'Juillet','Août','Septembre','Octobre','Novembre','Décembre'];
  const dateStr = jours[now.getDay()] + ' ' + now.getDate() + ' ' + mois[now.getMonth()] + ' ' + now.getFullYear();
  setText('pres-date',   dateStr);
  setText('pres-societe', currentSociete || 'Non définie');

  chargerEmployes();
  chargerHistorique();
}

// ─── Charger la liste des employés depuis Firestore ─────────
async function chargerEmployes() {
  if (!currentSociete) return;
  try {
    const snap = await db.collection('employes')
      .where('societe', '==', currentSociete)
      .orderBy('nom')
      .get();

    employes = snap.docs.map(d => ({ id: d.id, nom: d.data().nom }));
    pointages = {};
    employes.forEach(e => { pointages[e.nom] = 'present'; });
    renderListe();
    updateCompteurs();
  } catch (err) {
    console.error('Erreur chargement employés :', err);
  }
}

// ─── Afficher la liste des employés ─────────────────────────
function renderListe() {
  const container = document.getElementById('pres-list');
  if (!container) return;

  if (employes.length === 0) {
    container.innerHTML = `
      <div class="pres-empty">
        <i class="fa fa-user-slash"></i>
        <p>Aucun employé. Appuyez sur "Ajouter".</p>
      </div>`;
    return;
  }

  container.innerHTML = employes.map(emp => `
    <div class="pres-row" id="row-${emp.id}">
      <div class="pres-row-left">
        <div class="pres-avatar">${emp.nom.charAt(0).toUpperCase()}</div>
        <span class="pres-nom">${emp.nom}</span>
      </div>
      <div class="pres-btns">
        <button class="pres-btn pres-btn-present  ${pointages[emp.nom]==='present' ?'active':''}"
                onclick="setStatut('${emp.nom}','present')">
          <i class="fa fa-check"></i>
        </button>
        <button class="pres-btn pres-btn-retard   ${pointages[emp.nom]==='retard'  ?'active':''}"
                onclick="setStatut('${emp.nom}','retard')">
          <i class="fa fa-clock"></i>
        </button>
        <button class="pres-btn pres-btn-absent   ${pointages[emp.nom]==='absent'  ?'active':''}"
                onclick="setStatut('${emp.nom}','absent')">
          <i class="fa fa-xmark"></i>
        </button>
        <button class="pres-btn pres-btn-delete" onclick="supprimerEmploye('${emp.id}','${emp.nom}')">
          <i class="fa fa-trash"></i>
        </button>
      </div>
    </div>
  `).join('');
}

// ─── Changer le statut d'un employé ─────────────────────────
function setStatut(nom, statut) {
  pointages[nom] = statut;
  renderListe();
  updateCompteurs();
}

// ─── Mettre à jour les compteurs ────────────────────────────
function updateCompteurs() {
  const vals   = Object.values(pointages);
  setText('count-present', vals.filter(v => v === 'present').length);
  setText('count-retard',  vals.filter(v => v === 'retard').length);
  setText('count-absent',  vals.filter(v => v === 'absent').length);
}

// ─── Ajouter un employé ─────────────────────────────────────
function showAddEmploye() {
  document.getElementById('emp-overlay').classList.add('active');
  document.getElementById('emp-modal').classList.add('active');
  setTimeout(() => document.getElementById('emp-input').focus(), 300);
}

function closeAddEmploye() {
  document.getElementById('emp-overlay').classList.remove('active');
  document.getElementById('emp-modal').classList.remove('active');
  document.getElementById('emp-input').value = '';
}

async function confirmAddEmploye() {
  const nom = document.getElementById('emp-input').value.trim();
  if (!nom) return;

  // 1. Mise à jour immédiate de l'écran (pas d'attente)
  const tempId = 'temp_' + Date.now();
  employes.push({ id: tempId, nom });
  pointages[nom] = 'present';
  renderListe();
  updateCompteurs();
  closeAddEmploye();

  // 2. Sauvegarde Firestore en arrière-plan
  try {
    const ref = await db.collection('employes').add({
      nom,
      societe  : currentSociete,
      ajouteLe : firebase.firestore.FieldValue.serverTimestamp(),
      ajoutePar: currentUser.uid
    });
    // Remplacer l'ID temporaire par le vrai ID Firestore
    const emp = employes.find(e => e.id === tempId);
    if (emp) emp.id = ref.id;
  } catch (err) {
    // En cas d'erreur : retirer l'employé de la liste locale
    employes = employes.filter(e => e.id !== tempId);
    delete pointages[nom];
    renderListe();
    updateCompteurs();
    alert('Erreur sauvegarde : ' + err.message);
  }
}

// ─── Supprimer un employé ────────────────────────────────────
async function supprimerEmploye(id, nom) {
  if (!confirm(`Supprimer "${nom}" ?`)) return;
  try {
    await db.collection('employes').doc(id).delete();
    employes  = employes.filter(e => e.id !== id);
    delete pointages[nom];
    renderListe();
    updateCompteurs();
  } catch (err) {
    alert('Erreur : ' + err.message);
  }
}

// ─── Enregistrer le pointage dans Firestore ─────────────────
async function savePointage() {
  if (employes.length === 0) {
    alert('Ajoutez au moins un employé avant d\'enregistrer.');
    return;
  }

  const btn = document.getElementById('pres-save-btn');
  const txt = document.getElementById('pres-save-txt');
  btn.disabled = true;
  txt.textContent = 'Enregistrement…';

  const now     = new Date();
  const dateKey = now.toISOString().slice(0, 10); // YYYY-MM-DD

  const detail = employes.map(e => ({
    nom    : e.nom,
    statut : pointages[e.nom] || 'absent'
  }));

  const data = {
    date        : firebase.firestore.Timestamp.fromDate(now),
    dateKey,
    societe     : currentSociete,
    enregistrePar: currentUser.uid,
    nomManager  : currentUser.displayName || '',
    nbPresent   : detail.filter(d => d.statut === 'present').length,
    nbRetard    : detail.filter(d => d.statut === 'retard').length,
    nbAbsent    : detail.filter(d => d.statut === 'absent').length,
    total       : detail.length,
    detail
  };

  try {
    const docId = `${dateKey}_${currentSociete.replace(/\s+/g,'_')}`;
    await db.collection('pointages').doc(docId).set(data);

    txt.textContent = '✓ Pointage enregistré !';
    setTimeout(() => {
      txt.textContent = 'Enregistrer le pointage';
      btn.disabled = false;
    }, 2500);

    chargerHistorique();
  } catch (err) {
    alert('Erreur enregistrement : ' + err.message);
    txt.textContent = 'Enregistrer le pointage';
    btn.disabled = false;
  }
}

// ─── Charger l'historique récent ─────────────────────────────
async function chargerHistorique() {
  if (!currentSociete) return;
  const container = document.getElementById('pres-history');
  if (!container) return;

  try {
    const snap = await db.collection('pointages')
      .where('societe', '==', currentSociete)
      .orderBy('date', 'desc')
      .limit(5)
      .get();

    if (snap.empty) {
      container.innerHTML = `<div class="pres-empty"><i class="fa fa-inbox"></i><p>Aucun historique</p></div>`;
      return;
    }

    container.innerHTML = snap.docs.map(d => {
      const data  = d.data();
      const dt    = data.date.toDate();
      const label = dt.toLocaleDateString('fr-FR', { weekday:'short', day:'2-digit', month:'short' });
      return `
        <div class="pres-hist-row">
          <div class="pres-hist-left">
            <i class="fa fa-calendar-check"></i>
            <span>${label}</span>
          </div>
          <div class="pres-hist-right">
            <span class="hist-badge green">${data.nbPresent} ✓</span>
            <span class="hist-badge orange">${data.nbRetard} ⏰</span>
            <span class="hist-badge red">${data.nbAbsent} ✗</span>
          </div>
        </div>`;
    }).join('');
  } catch (err) {
    console.error('Erreur historique :', err);
  }
}

// ─── Utilitaire ──────────────────────────────────────────────
function setText(id, val) {
  const el = document.getElementById(id);
  if (el) el.textContent = val;
}

// ─── Déclencher l'init quand on navigue vers la page ────────
const _origNavigateTo = window.navigateTo;
window.navigateTo = function(pageId) {
  _origNavigateTo && _origNavigateTo(pageId);
  if (pageId === 'profil' && currentUser) initPresencePage();
};
