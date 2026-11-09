// Chargé avant le reste de la page, seulement dans l'appli Android.
// La WebView d'Android n'a ni synthèse vocale web, ni verrou d'écran, ni plein écran sur un bloc :
// ce fichier les remplace par les fonctions natives de l'appli (voir MainActivity.java).
(function () {
  var A = window.PokerLabAndroid;
  if (!A) return;

  // --- Synthèse vocale : redirigée vers le moteur TextToSpeech d'Android ---
  var listeners = [];
  function voices() {
    try { return JSON.parse(A.voices()); } catch (e) { return []; }
  }
  var synth = {
    speaking: false,
    pending: false,
    paused: false,
    onvoiceschanged: null,
    getVoices: voices,
    speak: function (u) {
      if (!u) return;
      var lang = (u.voice && u.voice.lang) || u.lang || "fr-FR";
      var rate = typeof u.rate === "number" ? u.rate : 1;
      var pitch = typeof u.pitch === "number" ? u.pitch : 1;
      A.speak(String(u.text || ""), String(lang), rate, pitch);
    },
    cancel: function () { A.stop(); },
    pause: function () {},
    resume: function () {},
    addEventListener: function (type, fn) { if (type === "voiceschanged") listeners.push(fn); },
    removeEventListener: function (type, fn) { listeners = listeners.filter(function (f) { return f !== fn; }); }
  };
  window.__pokerLabVoicesReady = function () {
    listeners.forEach(function (f) { try { f(); } catch (e) {} });
    if (typeof synth.onvoiceschanged === "function") { try { synth.onvoiceschanged(); } catch (e) {} }
  };
  try { Object.defineProperty(window, "speechSynthesis", { value: synth, configurable: true }); } catch (e) { window.speechSynthesis = synth; }

  function Utterance(text) {
    this.text = text || ""; this.lang = ""; this.voice = null;
    this.rate = 1; this.pitch = 1; this.volume = 1;
  }
  window.SpeechSynthesisUtterance = Utterance;

  // --- Écran toujours allumé : déjà géré par l'appli, on répond simplement « d'accord » ---
  if (!navigator.wakeLock) {
    try {
      Object.defineProperty(navigator, "wakeLock", {
        value: { request: function () { return Promise.resolve({ released: false, release: function () { return Promise.resolve(); }, addEventListener: function () {} }); } },
        configurable: true
      });
    } catch (e) {}
  }

  // --- Plein écran : l'appli est déjà en plein écran, la page utilise son propre mode « horloge plein écran » ---
  try { delete Element.prototype.requestFullscreen; } catch (e) {}
  try { Element.prototype.requestFullscreen = undefined; } catch (e) {}
})();
