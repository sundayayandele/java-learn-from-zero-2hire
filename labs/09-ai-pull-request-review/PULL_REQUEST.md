# PR #214: Add transfer fees and daily limits (generated with an AI assistant)

**Author:** junior developer, with an AI coding assistant · **Ticket:** ITL-388 · **Status:** awaiting review

> Implemented `TransferFeeService` as described in ITL-388. The assistant wrote the code and the tests in
> `TransferFeeServiceTest`, which all pass. Ready to merge!

## ITL-388: acceptance criteria (from the product owner)

1. **NGN transfer fee (VAT inclusive):** amounts up to and including ₦5,000.00 pay ₦10.75; above ₦5,000.00 up to and including ₦50,000.00 pay ₦26.88; above ₦50,000.00 pay ₦53.75.
2. **USD transfer fee:** 0.5% of the amount, at least $1.00 and at most $25.00, rounded to cents with banker's rounding (HALF_EVEN). Exact decimal arithmetic only.
3. Any other currency is rejected with `IllegalArgumentException`; it must never be charged a fee of zero.
4. **Daily limit:** each account may send at most ₦5,000,000.00 per day, counting amount plus fee. `authorise` returns false (and records nothing) when a transfer would exceed it. It is called concurrently by many request threads.
5. **Privacy:** logs must never contain a full account number; at most the last four digits.
