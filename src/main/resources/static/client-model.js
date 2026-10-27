// Presentation helpers only. Clinical interpretation and entitlement remain server-side.
export const LABELS = Object.freeze({
  '8480-6': 'Systolic blood pressure', '8462-4': 'Diastolic blood pressure',
  '8302-2': 'Height', '29463-7': 'Weight', '39156-5': 'BMI',
  '718-7': 'Haemoglobin', '2951-2': 'Sodium', '2947-0': 'Potassium (task convention)',
  '2160-0': 'Creatinine', '4548-4': 'HbA1c', '98979-8': 'eGFR',
  '85354-9': 'Blood pressure panel'
});

export const FLAG_LABELS = Object.freeze({
  NORMAL: 'Normal ○', HIGH: 'High ▲', LOW: 'Low ▼',
  NO_APPLICABLE_FLAG: 'No applicable flag □'
});

export function words(value) {
  return value == null ? 'Not recorded' : String(value).replaceAll('_', ' ').toLowerCase();
}

export function flagLabel(flag) { return FLAG_LABELS[flag] ?? 'Flag unavailable'; }
export function rangeLabel(range) {
  return range && Number.isFinite(range.low) && Number.isFinite(range.high)
    ? `${range.low}–${range.high}` : 'No applicable range';
}

export function valueLabel(result) {
  if (typeof result.value === 'number' && Number.isFinite(result.value)) {
    return `${result.value} ${result.unit ?? ''}`.trim();
  }
  if (result.cannotComputeReason) return `Cannot compute — ${words(result.cannotComputeReason)}`;
  return 'No numeric value';
}

// Keep server order and the full timestamp text. Relative nanoseconds prevent
// sub-millisecond observations collapsing because of Date's millisecond precision.
export function timestampNs(value) {
  if (typeof value !== 'string') return null;
  const match = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?Z$/.exec(value);
  if (!match) return null;
  const millis = Date.parse(`${match[1]}Z`);
  return Number.isFinite(millis)
    ? BigInt(millis) * 1000000n + BigInt((match[2] ?? '').padEnd(9, '0')) : null;
}

export function plotModel(points) {
  const entries = points.map(point => ({ point, time: timestampNs(point.effective) }));
  const times = entries.filter(entry => entry.time !== null).map(entry => entry.time);
  if (!times.length) return { segments: [], markers: [], gaps: points.length, min: null, max: null };
  const first = times.reduce((a, b) => a < b ? a : b);
  const last = times.reduce((a, b) => a > b ? a : b);
  const values = entries.filter(entry => entry.time !== null &&
    typeof entry.point.value === 'number' && Number.isFinite(entry.point.value)).map(entry => entry.point.value);
  if (!values.length) return { segments: [], markers: [], gaps: points.length, min: null, max: null };
  const low = values.reduce((a, b) => Math.min(a, b)), high = values.reduce((a, b) => Math.max(a, b));
  const padding = low === high ? Math.max(Math.abs(low) * 0.05, 1) : (high - low) * 0.1;
  const min = low - padding, max = high + padding;
  const segments = [], markers = [];
  let segment = [], gaps = 0;
  for (const entry of entries) {
    const valid = entry.time !== null && typeof entry.point.value === 'number' && Number.isFinite(entry.point.value);
    if (!valid) {
      if (segment.length) segments.push(segment);
      segment = []; gaps++; continue;
    }
    const marker = {
      point: entry.point,
      x: last === first ? 0.5 : Number(entry.time - first) / Number(last - first),
      y: (max - entry.point.value) / (max - min)
    };
    segment.push(marker); markers.push(marker);
  }
  if (segment.length) segments.push(segment);
  return { segments, markers, gaps, min, max };
}

// Abort is an optimization; identity/epoch checks are the privacy boundary against
// a late promise settling after context edits, navigation or a newer same-view read.
export class RequestGate {
  #epoch = 0;
  #requests = new Map();
  begin(channel) {
    this.cancel(channel);
    const token = { epoch: this.#epoch, controller: new AbortController(), timedOut: false };
    this.#requests.set(channel, token);
    return token;
  }
  current(channel, token) { return token.epoch === this.#epoch && this.#requests.get(channel) === token; }
  finish(channel, token) { if (this.current(channel, token)) this.#requests.delete(channel); }
  cancel(channel) {
    this.#requests.get(channel)?.controller.abort();
    this.#requests.delete(channel);
  }
  invalidate() {
    this.#epoch++;
    for (const token of this.#requests.values()) token.controller.abort();
    this.#requests.clear();
  }
}
