// Aviso sonoro con WebAudio (sin archivos). Los navegadores exigen un gesto del usuario antes de reproducir audio.

let ctx: AudioContext | null = null;

function getCtx(): AudioContext | null {
  try {
    const Ctor = window.AudioContext ?? (window as any).webkitAudioContext;
    if (!Ctor) return null;
    ctx ??= new Ctor() as AudioContext;
    return ctx;
  } catch { return null; }
}

/** Desbloquea el audio con el primer clic/toque/tecla. */
export function installAudioUnlock() {
  const unlock = () => {
    const c = getCtx();
    if (c && c.state === 'suspended') void c.resume();
    window.removeEventListener('pointerdown', unlock);
    window.removeEventListener('keydown', unlock);
  };
  window.addEventListener('pointerdown', unlock);
  window.addEventListener('keydown', unlock);
}

export function playNotificationSound() {
  const c = getCtx();
  if (!c) return;
  if (c.state === 'suspended') void c.resume().catch(() => undefined);
  const now = c.currentTime;
  [[880, 0], [1175, 0.16]].forEach(([freq, offset]) => {
    const osc = c.createOscillator();
    const gain = c.createGain();
    osc.type = 'sine';
    osc.frequency.value = freq;
    gain.gain.setValueAtTime(0.0001, now + offset);
    gain.gain.exponentialRampToValueAtTime(0.25, now + offset + 0.02);
    gain.gain.exponentialRampToValueAtTime(0.0001, now + offset + 0.3);
    osc.connect(gain).connect(c.destination);
    osc.start(now + offset);
    osc.stop(now + offset + 0.32);
  });
}

const KEY = 'almacen.sound';
export function readLocalSound(): boolean {
  try { return localStorage.getItem(KEY) !== 'off'; } catch { return true; }
}
export function writeLocalSound(on: boolean) {
  try { localStorage.setItem(KEY, on ? 'on' : 'off'); } catch { /* ignorar */ }
}
