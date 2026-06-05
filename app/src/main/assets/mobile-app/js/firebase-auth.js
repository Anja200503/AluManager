'use strict';

// ============================================================
//  CONFIGURATION FIREBASE
//  Remplace les valeurs ci-dessous par celles de ta console :
//  https://console.firebase.google.com → Ton projet → Paramètres
// ============================================================
const firebaseConfig = {
  apiKey:            "AIzaSyDYEknTIVOiFvzQsXiiuFDW_qm9shhZfV8",
  authDomain:        "alu-manager.firebaseapp.com",
  projectId:         "alu-manager",
  storageBucket:     "alu-manager.firebasestorage.app",
  messagingSenderId: "1017863578454",
  appId:             "1:1017863578454:web:6662a753e93f15d6d0acf7",
  measurementId:     "G-8L169QS8GG"
};

// Initialisation Firebase
firebase.initializeApp(firebaseConfig);
const auth = firebase.auth();
auth.languageCode = 'fr';

// ============================================================
//  GESTION DES ONGLETS
// ============================================================
function switchTab(tab) {
  const isLogin = tab === 'login';
  document.getElementById('form-login').style.display    = isLogin ? 'flex' : 'none';
  document.getElementById('form-register').style.display = isLogin ? 'none' : 'flex';
  document.getElementById('tab-login').classList.toggle('active', isLogin);
  document.getElementById('tab-register').classList.toggle('active', !isLogin);
  clearAuthError();
}

// ============================================================
//  MESSAGES D'ERREUR
// ============================================================
const errorMessages = {
  'auth/invalid-email':          'Adresse email invalide.',
  'auth/user-not-found':         'Aucun compte trouvé avec cet email.',
  'auth/wrong-password':         'Mot de passe incorrect.',
  'auth/email-already-in-use':   'Cet email est déjà utilisé.',
  'auth/weak-password':          'Le mot de passe doit contenir au moins 6 caractères.',
  'auth/too-many-requests':      'Trop de tentatives. Réessaie plus tard.',
  'auth/network-request-failed': 'Erreur réseau. Vérifie ta connexion.',
};

function showAuthError(msg) {
  const el = document.getElementById('auth-error');
  el.textContent = msg;
  el.style.display = 'block';
}

function clearAuthError() {
  const el = document.getElementById('auth-error');
  el.textContent = '';
  el.style.display = 'none';
}

function setLoading(btnId, loaderId, loading) {
  document.querySelector('#' + btnId + ' .auth-btn-text').style.display   = loading ? 'none'  : 'inline';
  document.querySelector('#' + btnId + ' .auth-btn-loader').style.display = loading ? 'inline': 'none';
  document.getElementById(btnId).disabled = loading;
}

// ============================================================
//  CONNEXION
// ============================================================
function handleLogin(e) {
  e.preventDefault();
  clearAuthError();
  const email    = document.getElementById('login-email').value.trim();
  const password = document.getElementById('login-password').value;

  setLoading('btn-login', 'btn-login', true);

  auth.signInWithEmailAndPassword(email, password)
    .catch(err => {
      showAuthError(errorMessages[err.code] || 'Erreur : ' + err.message);
      setLoading('btn-login', 'btn-login', false);
    });
}

// ============================================================
//  INSCRIPTION
// ============================================================
function handleRegister(e) {
  e.preventDefault();
  clearAuthError();
  const name     = document.getElementById('reg-name').value.trim();
  const email    = document.getElementById('reg-email').value.trim();
  const societe  = document.getElementById('reg-societe').value;
  const password = document.getElementById('reg-password').value;
  const confirm  = document.getElementById('reg-confirm').value;

  if (!societe) {
    showAuthError('Veuillez choisir votre société / lieu.');
    return;
  }
  if (password !== confirm) {
    showAuthError('Les mots de passe ne correspondent pas.');
    return;
  }

  setLoading('btn-register', 'btn-register', true);

  auth.createUserWithEmailAndPassword(email, password)
    .then(cred => cred.user.updateProfile({
      displayName: name,
      photoURL: societe
    }))
    .catch(err => {
      showAuthError(errorMessages[err.code] || 'Erreur : ' + err.message);
      setLoading('btn-register', 'btn-register', false);
    });
}

// ============================================================
//  DECONNEXION
// ============================================================
function handleLogout() {
  auth.signOut();
}

// ============================================================
//  SPLASH SCREEN — disparaît après vérification Firebase
// ============================================================
function hideSplash() {
  const splash = document.getElementById('splash-screen');
  if (!splash) return;
  splash.classList.add('fade-out');
  // Supprime du DOM après la transition pour libérer les ressources
  setTimeout(() => { if (splash.parentNode) splash.parentNode.removeChild(splash); }, 450);
}

// ============================================================
//  ETAT D'AUTHENTIFICATION — masque/affiche l'app
// ============================================================
auth.onAuthStateChanged(user => {
  const authScreen = document.getElementById('auth-screen');
  const appShell   = document.getElementById('app-shell');

  if (user) {
    // Utilisateur déjà connecté → accueil direct, pas de flash login
    appShell.style.display   = 'block';
    authScreen.style.display = 'none';

    // Restaure la page depuis le hash URL (retour depuis commande.html, etc.)
    if (typeof window._restoreFromHash === 'function') window._restoreFromHash();

    // Remplir le panneau compte
    const societe = user.photoURL || '';
    const creeLe  = user.metadata.creationTime
      ? new Date(user.metadata.creationTime).toLocaleDateString('fr-FR', { day:'2-digit', month:'long', year:'numeric' })
      : '—';

    setText('acct-name',    user.displayName || 'Utilisateur');
    setText('acct-societe', societe);
    setText('acct-email',   user.email || '—');
    setText('acct-societe2',societe || '—');
    setText('acct-date',    creeLe);
  } else {
    // Non connecté → afficher l'écran de login
    appShell.style.display   = 'none';
    authScreen.style.display = 'flex';
  }

  // Dans tous les cas, le splash disparaît maintenant
  hideSplash();
});

function setText(id, val) {
  const el = document.getElementById(id);
  if (el) el.textContent = val;
}
