import http from 'node:http';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { dirname, resolve, join } from 'node:path';
import { createHash } from 'node:crypto';
import { performance } from 'node:perf_hooks';
import os from 'node:os';

// Local synthetic assessment probe. No dependencies, service launch, database access,
// request retries, payload logging, hidden outlier filtering or concurrent requests.
// Usage: node measure-read-performance.mjs MANIFEST BASE_URL OUTPUT_JSON
const WARMUPS = 10;
const MEASURED = 100;
const TARGET_P95_MS = 500;
const REQUEST_TIMEOUT_MS = 15_000;
const RUN_TIMEOUT_MS = 600_000;
const MAX_BODY_BYTES = 4 * 1024 * 1024;
const RECIPE_HASH = 'f0a5f4cbbfd88d7f54dd27713862beb827e4f531bb8b5e40d74933f522345be6';
const CASE_LABELS = ['mixed_result_list', 'blood_pressure_detail', 'multi_year_sodium_trend', 'derived_egfr_trend', 'staff_access_log'];
const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');
const ensure = (condition, code) => { if (!condition) throw new Error(code); };
const isUuid = value => typeof value === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
const knownCodes = new Set(['ARGUMENTS_INVALID', 'BASE_NOT_LOCAL_ASSESSMENT', 'INPUT_CONFIGURATION_INVALID',
  'INPUT_MAPPING_INVALID', 'PREVIOUS_DATASET_VERIFICATION_REQUIRED', 'OUTPUT_ALREADY_EXISTS',
  'PREDECLARATION_ALREADY_EXISTS', 'RUN_TIME_BOUND_EXCEEDED', 'REQUEST_TIME_BOUND_EXCEEDED',
  'HTTP_TRANSPORT_FAILED', 'BODY_LIMIT_EXCEEDED', 'HTTP_STATUS_UNEXPECTED', 'RESPONSE_CONTRACT_FAILED',
  'RESPONSE_JSON_INVALID', 'NO_BLOOD_PRESSURE_MAPPING', 'UNEXPECTED_LOCAL_FAILURE']);
const safeError = error => knownCodes.has(error?.message) ? error.message : 'UNEXPECTED_LOCAL_FAILURE';

let outputPath;
let outputReserved = false;
let agent;
let activeRequest;
let runTimer;
let deadlineExpired = false;
const report = {
  schema: 'atlas-local-read-performance-v1', status: 'NOT_STARTED',
  target: { p95MsAtMost: TARGET_P95_MS, scope: 'Each measured case; chosen local assessment target, not a supplied requirement or production SLO.' },
  workload: { concurrency: 1, warmupsPerCase: WARMUPS, measuredPerCase: MEASURED, measuredRequestsPlanned: MEASURED * 5,
    warmupRequestsPlanned: WARMUPS * 5, mappingRequestsPlanned: 1, retries: 0,
    caseOrder: CASE_LABELS, orderWithinCase: 'All warmups followed by measured requests; fixed serial case order.' },
  timing: { clock: 'Node performance.now monotonic milliseconds',
    interval: 'Immediately before http.request through receipt of the final response-body byte; JSON parsing and assertions follow the timer.',
    percentile: 'Nearest-rank p95; p50 is median (mean of central two for even counts).',
    includes: 'Loopback HTTP, server authorization, reads/derivation, required audit commit, serialization and body transfer.',
    excludes: 'Browser rendering, TLS, remote network, cold startup, writes/ingestion and production load.' },
  bounds: { requestTimeoutMs: REQUEST_TIMEOUT_MS, wholeRunTimeoutMs: RUN_TIMEOUT_MS, maximumResponseBytes: MAX_BODY_BYTES },
  observations: [], cases: [], mappingRequestCount: 0, successfulHttpRequests: 0,
  clinicalSourceMutationRequests: 0,
  limitations: ['One synthetic dataset and one machine; fixed case order and warmed state are not a production benchmark.',
    'Manifest and prior verification are input evidence; persisted counts and query plans require the separate root-owned before/after database evidence.',
    'All successful read calls append an audit event. Access-log totals may therefore grow during this serial workload.',
    'No retries, failed samples, outliers or slow responses are silently dropped.']
};

function quantiles(values) {
  const sorted = [...values].sort((left, right) => left - right);
  if (sorted.length === 0) return null;
  const middle = Math.floor(sorted.length / 2);
  return { count: sorted.length,
    p50Ms: sorted.length % 2 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2,
    p95Ms: sorted[Math.ceil(0.95 * sorted.length) - 1], p99Ms: sorted[Math.ceil(0.99 * sorted.length) - 1], maxMs: sorted.at(-1), minMs: sorted[0],
    meanMs: sorted.reduce((sum, value) => sum + value, 0) / sorted.length };
}

async function requestJson(base, path, actorId, role, label, phase, sequence) {
  ensure(!deadlineExpired, 'RUN_TIME_BOUND_EXCEEDED');
  const sample = { case: label, phase, sequence, status: null, elapsedMs: null, bytes: 0, outcome: 'PENDING' };
  report.observations.push(sample);
  const started = performance.now();
  let requestTimer;
  try {
    const received = await new Promise((accept, reject) => {
      const request = http.request(new URL(path, base), { method: 'GET', agent,
        headers: { 'X-Test-Actor-Id': actorId, 'X-Test-Actor-Role': role, Accept: 'application/json', 'Accept-Encoding': 'identity' } }, response => {
        sample.status = response.statusCode;
        const chunks = [];
        response.on('data', chunk => {
          sample.bytes += chunk.length;
          if (sample.bytes > MAX_BODY_BYTES) { request.destroy(new Error('BODY_LIMIT_EXCEEDED')); return; }
          chunks.push(chunk);
        });
        response.on('end', () => {
          sample.elapsedMs = performance.now() - started;
          accept({ body: Buffer.concat(chunks), cacheControl: response.headers['cache-control'] });
        });
        response.on('error', () => reject(new Error('HTTP_TRANSPORT_FAILED')));
        response.on('aborted', () => reject(new Error('HTTP_TRANSPORT_FAILED')));
      });
      activeRequest = request;
      request.on('error', error => reject(error?.message === 'BODY_LIMIT_EXCEEDED' || error?.message === 'REQUEST_TIME_BOUND_EXCEEDED'
        || error?.message === 'RUN_TIME_BOUND_EXCEEDED' ? error : new Error('HTTP_TRANSPORT_FAILED')));
      requestTimer = setTimeout(() => request.destroy(new Error('REQUEST_TIME_BOUND_EXCEEDED')), REQUEST_TIMEOUT_MS);
      request.end();
    });
    ensure(sample.status === 200, 'HTTP_STATUS_UNEXPECTED');
    ensure(/(?:^|,)\s*no-store\s*(?:,|$)/i.test(received.cacheControl ?? ''), 'RESPONSE_CONTRACT_FAILED');
    let decoded;
    try { decoded = JSON.parse(received.body.toString('utf8')); } catch { throw new Error('RESPONSE_JSON_INVALID'); }
    sample.outcome = 'HTTP_200';
    report.successfulHttpRequests++;
    return decoded;
  } catch (error) {
    if (sample.elapsedMs === null) sample.elapsedMs = performance.now() - started;
    sample.outcome = safeError(error);
    throw error;
  } finally {
    clearTimeout(requestTimer);
    activeRequest = undefined;
  }
}

try {
  ensure(process.argv.length === 5, 'ARGUMENTS_INVALID');
  const manifestPath = resolve(process.argv[2]);
  const base = new URL(process.argv[3]);
  ensure(base.protocol === 'http:' && base.hostname === '127.0.0.1' && base.port === '18081'
    && base.pathname === '/' && base.search === '' && base.hash === '' && base.username === '' && base.password === '', 'BASE_NOT_LOCAL_ASSESSMENT');
  outputPath = resolve(process.argv[4]);
  const [manifestBytes, configBytes, verificationBytes] = await Promise.all([
    readFile(manifestPath), readFile(join(dirname(manifestPath), 'recipe-config.json')),
    readFile(join(dirname(manifestPath), 'verification.json'))]);
  const manifest = JSON.parse(manifestBytes);
  const config = JSON.parse(configBytes);
  const verification = JSON.parse(verificationBytes);
  ensure(config.version === 'atlas-g6-v1' && config.seed === 20261027 && config.asOf === '2026-10-27'
    && config.patients === 100 && config.sourceObservations === 10000 && config.recipeHash === RECIPE_HASH
    && manifest.version === config.version && manifest.seed === config.seed && manifest.asOf === config.asOf
    && manifest.configuredPatients === 100 && manifest.configuredObservations === 10000 && !!manifest.loadCompletedAtUtc, 'INPUT_CONFIGURATION_INVALID');
  ensure(Object.keys(manifest.patients ?? {}).length === 100 && Object.keys(manifest.encounters ?? {}).length === 106
    && Object.keys(manifest.observations ?? {}).length === 10000, 'INPUT_MAPPING_INVALID');
  ensure(verification.status === 'PASS' && verification.version === config.version && verification.patients === 100
    && verification.encounters === 106 && verification.sourceObservations === 10000, 'PREVIOUS_DATASET_VERIFICATION_REQUIRED');
  for (const key of ['p000', 'p006', 'p010']) {
    ensure(isUuid(manifest.patients[key]?.id) && manifest.patients[key]?.actorId === 'atlas-g6-v1-clinician-a', 'INPUT_MAPPING_INVALID');
  }
  const patients = manifest.patients;
  const patientPath = key => `/patients/${encodeURIComponent(patients[key].id)}`;
  report.inputEvidence = { version: config.version, seed: config.seed, semanticAsOf: config.asOf,
    patientsInManifest: 100, encountersInManifest: 106, sourceObservationsInManifest: 10000,
    recipeHash: RECIPE_HASH, manifestSha256: sha256(manifestBytes), recipeConfigSha256: sha256(configBytes),
    priorVerificationSha256: sha256(verificationBytes), priorVerificationAtUtc: verification.verifiedAtUtc };
  report.environment = { node: process.version, operatingSystem: os.type(), release: os.release(), architecture: process.arch,
    cpuModel: os.cpus()[0]?.model, logicalCpuCount: os.cpus().length, totalMemoryBytes: os.totalmem(), baseScope: 'HTTP loopback127.0.0.1:18081, existing application' };
  report.startedAtUtc = new Date().toISOString();
  report.status = 'RUNNING';
  await mkdir(dirname(outputPath), { recursive: true });
  // Reserve evidence paths before any HTTP request; existing evidence is never overwritten.
  try { await writeFile(outputPath, `${JSON.stringify(report, null, 2)}\n`, { flag: 'wx' }); outputReserved = true; }
  catch { throw new Error('OUTPUT_ALREADY_EXISTS'); }
  const declaration = { schema: report.schema, declaredAtUtc: new Date().toISOString(), target: report.target,
    workload: report.workload, bounds: report.bounds, inputEvidence: report.inputEvidence,
    mapping: 'One authorized p010 list request discovers an existing BP UUID; excluded from550 warmup/measured requests and included in audit accounting.',
    selection: { mixed_result_list: 'p010 mixed synthetic history, first50 rows', blood_pressure_detail: 'p010 first BP85354-9 in authorized list',
      multi_year_sodium_trend: 'p006 sodium2951-2,94 monthly points, full2019–2026 history',
      derived_egfr_trend: 'p010 derived98979-8 over all authorized history', staff_access_log: 'p000 STAFF, first50 rows; live audit history' } };
  const declarationBytes = `${JSON.stringify(declaration, null, 2)}\n`;
  try { await writeFile(`${outputPath}.declaration.json`, declarationBytes, { flag: 'wx' }); }
  catch { throw new Error('PREDECLARATION_ALREADY_EXISTS'); }
  report.declarationSha256 = sha256(declarationBytes);
  agent = new http.Agent({ keepAlive: true, maxSockets: 1, maxFreeSockets: 1 });
  runTimer = setTimeout(() => { deadlineExpired = true; activeRequest?.destroy(new Error('RUN_TIME_BOUND_EXCEEDED')); }, RUN_TIMEOUT_MS);
  const actor = patients.p010.actorId;
  const mapping = await requestJson(base, `${patientPath('p010')}/results?offset=0&limit=200`, actor, 'CLINICIAN', 'bp_mapping', 'mapping', 1);
  report.mappingRequestCount = 1;
  ensure(Array.isArray(mapping.items) && Number.isInteger(mapping.total) && mapping.total <= 200, 'RESPONSE_CONTRACT_FAILED');
  const bloodPressure = mapping.items.find(item => item.code === '85354-9' && item.derived !== true);
  ensure(isUuid(bloodPressure?.id) && Object.values(manifest.observations).includes(bloodPressure.id), 'NO_BLOOD_PRESSURE_MAPPING');
  const cases = [
    { label: CASE_LABELS[0], path: `${patientPath('p010')}/results?offset=0&limit=50`, role: 'CLINICIAN', actor,
      check: body => Array.isArray(body.items) && body.items.length > 0 && body.items.length <= 50 && body.total === mapping.total && body.offset === 0 && body.limit === 50 },
    { label: CASE_LABELS[1], path: `${patientPath('p010')}/results/${encodeURIComponent(bloodPressure.id)}`, role: 'CLINICIAN', actor,
      check: body => body.id === bloodPressure.id && body.code === '85354-9' && Array.isArray(body.components) && body.components.length === 2 },
    { label: CASE_LABELS[2], path: `${patientPath('p006')}/trend?code=2951-2`, role: 'CLINICIAN', actor: patients.p006.actorId,
      check: body => body.code === '2951-2' && Array.isArray(body.points) && body.total === 94 && body.points.length === 94 },
    { label: CASE_LABELS[3], path: `${patientPath('p010')}/trend?code=98979-8`, role: 'CLINICIAN', actor,
      check: body => body.code === '98979-8' && Array.isArray(body.points) && body.points.length > 0 && body.total === body.points.length && body.points.every(point => point.derived === true) },
    { label: CASE_LABELS[4], path: `${patientPath('p000')}/access-log?offset=0&limit=50`, role: 'STAFF', actor: 'atlas-g6-v1-staff',
      check: body => Array.isArray(body.items) && body.items.length > 0 && body.items.length <= 50 && body.total >= body.items.length && body.offset === 0 && body.limit === 50 }
  ];
  for (const item of cases) {
    console.log(`ATLAS_PERFORMANCE_CASE_STARTED ${item.label}`);
    for (const phase of ['warmup', 'measured']) {
      const count = phase === 'warmup' ? WARMUPS : MEASURED;
      for (let sequence = 1; sequence <= count; sequence++) {
        const body = await requestJson(base, item.path, item.actor, item.role, item.label, phase, sequence);
        if (!item.check(body)) {
          report.observations.at(-1).outcome = 'RESPONSE_CONTRACT_FAILED';
          throw new Error('RESPONSE_CONTRACT_FAILED');
        }
      }
    }
    const samples = report.observations.filter(sample => sample.case === item.label && sample.phase === 'measured');
    const stats = quantiles(samples.map(sample => sample.elapsedMs));
    report.cases.push({ case: item.label, ...stats, status200: samples.filter(sample => sample.status === 200).length,
      targetMet: stats.p95Ms <= TARGET_P95_MS, bodyBytesMinimum: Math.min(...samples.map(sample => sample.bytes)), bodyBytesMaximum: Math.max(...samples.map(sample => sample.bytes)) });
    await writeFile(outputPath, `${JSON.stringify(report, null, 2)}\n`);
    console.log(`ATLAS_PERFORMANCE_CASE_COMPLETED ${item.label} ${samples.length}`);
  }
  report.status = 'COMPLETED';
  report.targetMet = report.cases.every(item => item.targetMet);
  report.expectedAuditGrowthFromSuccessfulProbeRequests = report.successfulHttpRequests;
  report.finishedAtUtc = new Date().toISOString();
  ensure(report.successfulHttpRequests === 551 && report.observations.length === 551, 'RESPONSE_CONTRACT_FAILED');
  process.exitCode = report.targetMet ? 0 : 2;
} catch (error) {
  report.status = 'FAILED';
  report.errorCode = safeError(error);
  report.finishedAtUtc = new Date().toISOString();
  report.expectedAuditGrowthFromSuccessfulProbeRequests = report.successfulHttpRequests;
  process.exitCode = 2;
} finally {
  clearTimeout(runTimer);
  activeRequest?.destroy();
  agent?.destroy();
  // Do not replace previous evidence if the output reservation itself failed.
  if (outputReserved && report.errorCode !== 'PREDECLARATION_ALREADY_EXISTS') {
    try { await writeFile(outputPath, `${JSON.stringify(report, null, 2)}\n`); }
    catch { console.log('ATLAS_PERFORMANCE_EVIDENCE_WRITE_FAILED'); process.exitCode = 2; }
  }
  console.log(`ATLAS_PERFORMANCE_FINISHED ${report.status} ${report.successfulHttpRequests}`);
}
