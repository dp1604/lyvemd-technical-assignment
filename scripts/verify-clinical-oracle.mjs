import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

// Independent numerical oracle: piecewise NKF equations, not application Java.
// These calculations are not an independent clinical validation of the equation.
const sources = {
  niddk: 'https://www.niddk.nih.gov/research-funding/research-programs/kidney-clinical-research-epidemiology/laboratory/glomerular-filtration-rate-equations/adults',
  nkfExamples: 'https://www.kidney.org/sites/default/files/example_it_ticket-implement_2021_ckd-epi_equation_to_calculate_egfr_from_creatinine_1.pdf',
  loinc: 'https://loinc.org/98979-8',
  ucum: 'https://ucum.org/ucum'
};

function independentlyCalculate(age, sex, creatinineMgDl) {
  // The explicit branches provide a different expression from the min/max form.
  const female = sex === 'female';
  const breakpoint = female ? 0.7 : 0.9;
  const lowerExponent = female ? -0.241 : -0.302;
  const exponent = creatinineMgDl <= breakpoint ? lowerExponent : -1.2;
  return 142 * ((creatinineMgDl / breakpoint) ** exponent)
    * (0.9938 ** age) * (female ? 1.012 : 1);
}

// Published numbers below are NKF's whole-number results. Full-precision numbers
// generated below are our formula evaluations, never labelled published values.
const nkfRows = [
  [18, 'male', 0.90, 127], [18, 'male', 0.91, 125],
  [18, 'female', 0.70, 128], [18, 'female', 0.71, 126],
  [90, 'male', 0.50, 97], [90, 'male', 1.50, 44],
  [90, 'female', 0.50, 89], [90, 'female', 1.50, 33]
];
const valid = [];
for (const [age, sex, mgDl, publishedWholePerMinute] of nkfRows) {
  const perMinute = independentlyCalculate(age, sex, mgDl);
  if (Math.round(perMinute) !== publishedWholePerMinute) {
    throw new Error('Independent calculation did not reproduce published NKF rounded result');
  }
  for (const unitCode of ['mg/dL', 'umol/L']) {
    const value = unitCode === 'mg/dL' ? mgDl : Number((mgDl * 88.4).toFixed(10));
    const normalized = unitCode === 'mg/dL' ? value : value / 88.4;
    const normalizedPerMinute = independentlyCalculate(age, sex, normalized);
    valid.push({
      id: `nkf-${age}-${sex}-${mgDl.toFixed(2)}-${unitCode === 'mg/dL' ? 'mg' : 'umol'}`,
      ageYears: age, clinicalSex: sex, creatinine: value, unitCode,
      expectedCreatinineMgDl: mgDl,
      publishedWholePerMinute,
      expectedPerMinute: normalizedPerMinute,
      expectedPerSecond: normalizedPerMinute / 60,
      provenance: 'Published NKF rounded test example; full-precision values independently evaluated in JavaScript.'
    });
  }
}

const extraInputs = [
  [18, 'female', 0.699999], [18, 'female', 0.700001],
  [18, 'male', 0.899999], [18, 'male', 0.900001],
  [60, 'female', 1.00], [60, 'male', 1.00],
  [45, 'female', 1.20], [45, 'male', 1.20]
];
for (const [age, sex, mgDl] of extraInputs) {
  const perMinute = independentlyCalculate(age, sex, mgDl);
  valid.push({
    id: `independent-${age}-${sex}-${mgDl}`,
    ageYears: age, clinicalSex: sex, creatinine: mgDl, unitCode: 'mg/dL',
    expectedCreatinineMgDl: mgDl,
    expectedPerMinute: perMinute, expectedPerSecond: perMinute / 60,
    provenance: 'Additional independent formula calculation; not a published patient or worked example.'
  });
}

const unavailable = [
  { id: 'age17', ageYears: 17, clinicalSex: 'male', creatinine: 0.9, unitCode: 'mg/dL', reason: 'Adult equation inapplicable under age 18.' },
  { id: 'age-missing', ageYears: null, clinicalSex: 'male', creatinine: 0.9, unitCode: 'mg/dL', reason: 'No effective-time completed age.' },
  { id: 'sex-missing', ageYears: 18, clinicalSex: null, creatinine: 0.9, unitCode: 'mg/dL', reason: 'No applicable clinical sex assertion.' },
  { id: 'sex-unknown', ageYears: 18, clinicalSex: 'unknown', creatinine: 0.9, unitCode: 'mg/dL', reason: 'Unknown clinical sex; do not guess a branch.' },
  { id: 'standardization-unverified', ageYears: 18, clinicalSex: 'male', creatinine: 0.9, unitCode: 'mg/dL', standardizationVerified: false, reason: 'Assay standardization unverified; Quantity and LOINC alone are insufficient.' },
  { id: 'creatinine-missing', ageYears: 18, clinicalSex: 'male', creatinine: null, unitCode: 'mg/dL', reason: 'Missing input; never substitute zero or a normal value.' },
  { id: 'creatinine-zero', ageYears: 18, clinicalSex: 'male', creatinine: 0, unitCode: 'mg/dL', reason: 'Nonpositive creatinine is unsuitable for the formula; explicit unavailable result.' },
  { id: 'creatinine-negative', ageYears: 18, clinicalSex: 'male', creatinine: -1, unitCode: 'mg/dL', reason: 'Nonpositive creatinine is unsuitable for the formula; explicit unavailable result.' }
];
const invalidRepresentation = [
  { id: 'nonfinite-nan', representation: 'NaN', reason: 'Not a valid finite clinical number; reject ingestion.' },
  { id: 'nonfinite-infinity', representation: 'Infinity', reason: 'Not a valid finite clinical number; reject ingestion.' },
  { id: 'micro-display-as-code', creatinine: 88.4, unitCode: '\u00b5mol/L', reason: 'Display glyph is not the accepted UCUM machine code umol/L; reject unsupported representation.' },
  { id: 'wrong-unit', creatinine: 1, unitCode: 'mmol/L', reason: 'Not one of the declared accepted creatinine unit representations; reject ingestion.' }
];

const out = {
  description: 'Independent Group 3 clinical oracle. Synthetic calculations and official published rounded examples are explicitly distinguished.',
  checkedDateLocal: '2026-10-27', sources,
  equation: { name: 'CKD-EPI 2021 creatinine, race-free', coefficient: 142, ageFactor: 0.9938,
    femaleKappa: 0.7, maleKappa: 0.9, femaleLowExponent: -0.241, maleLowExponent: -0.302,
    highExponent: -1.2, femaleFactor: 1.012, minimumAgeYears: 18 },
  conversion: { creatinineUmolPerMgDl: 88.4, secondsPerMinute: 60,
    canonicalCreatinineUnit: 'mg/dL', derivedUnitCode: 'mL/s/{1.73_m2}', derivedDisplayUnit: 'mL/s/1.73m2', loinc: '98979-8' },
  tolerances: { canonicalCreatinineAbsolute: 1e-12, egfrPerMinuteAbsolute: 1e-9,
    egfrPerSecondAbsolute: 1e-11, publishedWholeNumber: 'Math.round(unroundedPerMinute) must exactly equal NKF published integer; do not compare an unrounded value exactly to the rounded integer.' },
  valid, unavailable, invalidRepresentation,
  limitations: [
    'This independently transcribes the same published equation; it is not an independent physiological or assay validation.',
    'No clinical assay interval or new clinical cutoffs are invented. Actual assay interval/standardization verification remains an upstream production gate.',
    'Do not silently import NKF example-ticket reporting cutoffs or whole-number formatting into the assignment per-second contract.',
    'Input age is completed years at the observation effective UTC date under the adopted policy, not age today.',
    'Ranges are the task table; these sources do not replace it with different clinical intervals.'
  ]
};
const fixturePath = join(dirname(fileURLToPath(import.meta.url)), '../src/test/resources/clinical/egfr-oracle.json');
const fixture = JSON.parse(readFileSync(fixturePath, 'utf8'));
if (fixture.valid.length !== valid.length) throw new Error('Oracle case inventory differs');
for (const reference of valid) {
  const saved = fixture.valid.find(row => row.id === reference.id);
  if (!saved || saved.unitCode !== reference.unitCode || saved.ageYears !== reference.ageYears || saved.clinicalSex !== reference.clinicalSex
      || saved.publishedWholePerMinute !== reference.publishedWholePerMinute) throw new Error('Oracle input/provenance differs');
  for (const [field, tolerance] of [['expectedCreatinineMgDl', 1e-12], ['expectedPerMinute', 1e-9], ['expectedPerSecond', 1e-11]]) {
    if (!Number.isFinite(saved[field]) || Math.abs(saved[field] - reference[field]) > tolerance) throw new Error('Oracle numeric comparison failed');
  }
}
console.log(JSON.stringify({status:'PASS',publishedExamplesVerified:nkfRows.length,validCasesCompared:valid.length,
  limits:'Separate piecewise JavaScript versus published rounded NKF examples and saved fixture; not independent physiological/assay validation.'}));