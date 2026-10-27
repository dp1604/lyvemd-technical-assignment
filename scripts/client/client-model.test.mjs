import test from 'node:test';
import assert from 'node:assert/strict';
import { flagLabel, valueLabel, rangeLabel, timestampNs, plotModel, RequestGate } from '../../src/main/resources/static/client-model.js';

test('missing clinical interpretation is never presented as normal or numeric zero', () => {
  assert.equal(flagLabel('NO_APPLICABLE_FLAG'), 'No applicable flag □');
  assert.notEqual(flagLabel('NO_APPLICABLE_FLAG'), flagLabel('NORMAL'));
  assert.equal(rangeLabel(null), 'No applicable range');
  assert.match(valueLabel({ value: null, cannotComputeReason: 'HEIGHT_UNAVAILABLE' }), /Cannot compute — height unavailable/);
  assert.equal(valueLabel({ value: 0, unit: 'kg' }), '0 kg');
});

test('numeric gaps break chart lines rather than fabricating zero or joining across gaps', () => {
  const values = [140, null, 142, 143];
  const points = values.map((value, index) => ({ value, effective: `2026-10-0${index + 1}T00:00:00Z` }));
  const result = plotModel(points);
  assert.equal(result.gaps, 1);
  assert.deepEqual(result.segments.map(segment => segment.length), [1, 2]);
  assert.deepEqual(result.markers.map(marker => marker.point.value), [140, 142, 143]);
  assert.ok(result.markers.every(marker => Number.isFinite(marker.x) && Number.isFinite(marker.y)));
});

test('all-null and single-valued trends have truthful finite geometry', () => {
  assert.equal(plotModel([{ effective: '2026-10-01T00:00:00Z', value: null }]).min, null);
  const result = plotModel([{ effective: '2026-10-01T00:00:00Z', value: 140 }]);
  assert.equal(result.markers[0].x, 0.5);
  assert.equal(result.markers[0].y, 0.5);
  assert.equal(plotModel([{ effective: 'invalid', value: 140 }]).markers.length, 0);
});

test('chart preserves server order and distinguishes nanoseconds in the same millisecond', () => {
  const points = [1, 2, 3].map(n => ({ value: n, effective: `2026-10-01T00:00:00.00000000${n}Z` }));
  const result = plotModel(points);
  assert.deepEqual(result.markers.map(marker => marker.x), [0, 0.5, 1]);
  assert.deepEqual(result.markers.map(marker => marker.point), points);
  assert.equal(timestampNs(points[2].effective) - timestampNs(points[0].effective), 2n);
});

test('context invalidation rejects a late successful promise even if abort is ignored', async () => {
  const gate = new RequestGate();
  const old = gate.begin('results');
  let complete;
  const late = new Promise(resolve => { complete = resolve; }).then(() => gate.current('results', old));
  gate.invalidate();
  const next = gate.begin('results');
  complete();
  assert.equal(await late, false);
  assert.equal(old.controller.signal.aborted, true);
  assert.equal(gate.current('results', next), true);
  gate.finish('results', old);
  assert.equal(gate.current('results', next), true);
});

test('newer same-view read and a closed detail independently reject stale completions', () => {
  const gate = new RequestGate();
  const old = gate.begin('results'), detail = gate.begin('detail'), current = gate.begin('results');
  assert.equal(gate.current('results', old), false);
  assert.equal(gate.current('detail', detail), true);
  gate.cancel('detail');
  assert.equal(gate.current('detail', detail), false);
  assert.equal(gate.current('results', current), true);
  gate.finish('results', current);
  assert.equal(gate.current('results', current), false);
});
