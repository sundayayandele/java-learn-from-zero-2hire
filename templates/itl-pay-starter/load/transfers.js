// ITL Pay load test (topic 13.5): customers send transfers and their apps poll the status.
// docker run --rm --network itl-pay-starter_default -v "$PWD/load:/scripts" grafana/k6:2.3.0 run /scripts/transfers.js
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://gateway:8080';

export const options = {
  scenarios: {
    salary_day: {
      executor: 'ramping-arrival-rate',   // a fixed rate of new transfers, like real traffic: it doesn't slow down when we do
      startRate: 5,
      timeUnit: '1s',
      preAllocatedVUs: 50,
      maxVUs: 200,
      stages: [
        { duration: '30s', target: 20 },  // ramp up to 20 transfers a second
        { duration: '1m', target: 20 },   // hold
        { duration: '30s', target: 60 },  // salary-day spike
        { duration: '1m', target: 60 },
        { duration: '20s', target: 0 },
      ],
    },
  },
  thresholds: {
    'http_req_failed': ['rate<0.01'],                                 // fewer than 1% errors
    'http_req_duration{name:create}': ['p(95)<500'],                  // the SLO: 95% of transfers answered within 500 ms
    'http_req_duration{name:status}': ['p(95)<100'],
  },
};

export default function () {
  const reference = `LOAD-${__VU}-${__ITER}-${Date.now()}`;
  const create = http.post(`${BASE}/api/transfers`,
    JSON.stringify({ reference, from: 'A1', to: 'A2', amount: 1.0 }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'create' } });
  check(create, { 'accepted (202)': (r) => r.status === 202 });

  for (let i = 0; i < 3; i++) {                                       // the app polls until it sees a final state
    sleep(0.5);
    const status = http.get(`${BASE}/api/transfers/${reference}`, { tags: { name: 'status' } });
    check(status, { 'status found': (r) => r.status === 200 });
  }
}
