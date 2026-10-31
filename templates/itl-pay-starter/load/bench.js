// One step of the reactive-versus-virtual-threads benchmark (topic 14.3): a constant arrival rate against one service.
// docker run --rm --network itl-pay-starter_default -v "$PWD/load:/scripts" -e TARGET=http://rates:8085 -e RATE=500 \
//     grafana/k6:2.3.0 run /scripts/bench.js
import http from 'k6/http';
import { check } from 'k6';

const TARGET = __ENV.TARGET || 'http://rates:8085';
const RATE = parseInt(__ENV.RATE || '500');

export const options = {
  scenarios: {
    steady: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: __ENV.DURATION || '30s',
      preAllocatedVUs: Math.max(50, RATE / 4),
      maxVUs: RATE * 2,                    // each request should take ~100 ms, so RATE / 10 in flight; room for slowness
    },
  },
  summaryTrendStats: ['p(50)', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const res = http.get(`${TARGET}${__ENV.PATH_ || '/bench/quote/USD-NGN'}`, { timeout: '5s' });
  check(res, { ok: (r) => r.status === 200 });
}
