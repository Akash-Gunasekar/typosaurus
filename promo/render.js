// node render.js beats   -> one frame every 0.5 s + contact sheet
// node render.js at 3 12 -> frames at given seconds
// node render.js audio   -> track.wav only (narration + explainroo music and sound effects)
// node render.js full    -> 60fps x 3 subframes, tmix, audio mux -> typosaurus-promo.mp4 (+ .srt, poster.jpg)
// Narration: node ~/.explainroo/bin/explainroo.js voice narration
const { chromium } = require('playwright');
const ffmpeg = require('ffmpeg-static');
const { execFileSync } = require('child_process');
const fs = require('fs'), path = require('path'), os = require('os');
const { pathToFileURL } = require('url');

const mode = process.argv[2] || 'beats';
const FPS = 60, SUB = 3;
const out = f => path.join(__dirname, f);
const VO = out('narration/build/voice');

async function renderTrack(A) {
  const lib = f => import(pathToFileURL(path.join(os.homedir(), '.explainroo/engine/audio', f)).href);
  const { renderSoundtrack, encodeWav } = await lib('soundtrack.js');
  const { parseWav, toMono } = await lib('pcm.js');
  const voice = A.voice.map(([id, start]) => {
    const b = fs.readFileSync(path.join(VO, id + '.wav'));
    const w = parseWav(b.buffer.slice(b.byteOffset, b.byteOffset + b.length));
    return { id, start, data: toMono(w.channels), sampleRate: w.sampleRate };
  });
  const spans = voice.map(v => ({ start: v.start, end: v.start + v.data.length / v.sampleRate }));
  const buf = await renderSoundtrack({
    duration: A.duration, voice, voiceSpans: spans,
    music: { style: 'upbeat', volume: 0.5 }, sfx: A.sfx, scenes: A.scenes.map(start => ({ start })),
  });
  fs.writeFileSync(out('track.wav'), Buffer.from(encodeWav(buf)));
  const ts = s => new Date(s * 1000).toISOString().slice(11, 23).replace('.', ',');
  fs.writeFileSync(out('typosaurus-promo.srt'), voice.map((v, i) => {
    const words = JSON.parse(fs.readFileSync(path.join(VO, v.id + '.json'), 'utf8')).words;
    return `${i + 1}\n${ts(spans[i].start)} --> ${ts(spans[i].end)}\n${words.map(w => w.text).join(' ')}\n`;
  }).join('\n'));
}

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } });
  await page.goto(pathToFileURL(out('promo.html')).href);
  await page.evaluate(() => window.ready);
  const A = await page.evaluate(() => window.AUDIO);

  if (mode === 'beats' || mode === 'at') {
    const dir = out('beats'); fs.rmSync(dir, { recursive: true, force: true }); fs.mkdirSync(dir);
    const times = mode === 'at' ? process.argv.slice(3).map(Number) : [...Array(60)].map((_, i) => i * 0.5 + 0.25);
    for (let i = 0; i < times.length; i++) {
      await page.evaluate(t => seek(t), times[i]);
      await page.screenshot({ path: path.join(dir, `b${String(i).padStart(2, '0')}.png`) });
    }
    if (mode === 'beats') {
      execFileSync(ffmpeg, ['-y', '-loglevel', 'error', '-i', path.join(dir, 'b%02d.png'),
        '-vf', 'scale=480:-1,tile=6x10:padding=4:color=red', '-frames:v', '1', out('sheet.png')]);
    }
  } else if (mode === 'audio') {
    await renderTrack(A);
  } else {
    await page.evaluate(() => seek(27.6));
    await page.screenshot({ path: out('poster.jpg'), type: 'jpeg', quality: 92 });
    const dir = out('frames'); fs.rmSync(dir, { recursive: true, force: true }); fs.mkdirSync(dir);
    const total = Math.round(FPS * SUB * A.duration);
    const t0 = Date.now();
    for (let i = 0; i < total; i++) {
      await page.evaluate(t => seek(t), i / (FPS * SUB));
      await page.screenshot({ path: path.join(dir, `f${String(i).padStart(5, '0')}.jpg`), type: 'jpeg', quality: 95 });
      if (i % 180 === 0) console.log(`frame ${i}/${total}  ${((Date.now() - t0) / 1000).toFixed(0)}s`);
    }
    await renderTrack(A);
    execFileSync(ffmpeg, ['-y', '-loglevel', 'error',
      '-framerate', String(FPS * SUB), '-i', path.join(dir, 'f%05d.jpg'),
      '-i', out('track.wav'),
      '-vf', `tmix=frames=${SUB},select='eq(mod(n\\,${SUB})\\,${SUB - 1})',setpts=N/${FPS}/TB`,
      '-af', 'loudnorm=I=-14:TP=-1.5:LRA=11', '-ar', '48000',
      '-r', String(FPS), '-c:v', 'libx264', '-preset', 'slow', '-crf', '16', '-pix_fmt', 'yuv420p',
      '-c:a', 'aac', '-b:a', '256k', '-shortest', '-movflags', '+faststart',
      out('typosaurus-promo.mp4')], { stdio: 'inherit' });
    console.log('done');
  }
  await browser.close();
})();
