# Research References

Papers/standards relevant to this system's architecture and dependencies.

## Async / queue architecture

- Welsh, Culler, Brewer — [SEDA: An Architecture for Well-Conditioned, Scalable Internet Services](https://www.sosp.org/2001/papers/welsh.pdf) (SOSP 2001).
  Matches the stage-queue-worker design: upload -> queue -> normalize -> queue -> transcribe.
- Gilbert & Lynch — "Brewer's Conjecture and the Feasibility of Consistent, Available, Partition-Tolerant Web Services" (2002).
  Relevant to the DB-write + MQ-publish dual-write gap (not atomic) noted in [14-questions.md](./14-questions.md) / [17-issues.md](./17-issues.md).

## Transactional messaging

- Transactional outbox pattern (see Kleppmann, *Designing Data-Intensive Applications*, ch. 11, for the standard treatment).
  Directly applicable: API writes to Postgres then publishes to RabbitMQ non-atomically today.

## Speech / audio processing

- Radford et al. — [Robust Speech Recognition via Large-Scale Weak Supervision](https://arxiv.org/abs/2212.04356) (Whisper, OpenAI 2022).
  Underlies `workers/transcription_worker.py` (`openai-whisper` package). Documents model-size/language tradeoffs affecting transcription throughput.
- ITU-R BS.1770 / EBU R128 loudness normalization standard.
  Basis for FFmpeg's `loudnorm` filter used in `workers/normalization_worker.py`. Useful for justifying target LUFS instead of defaults.

## Load testing / performance analysis

- Little, J.D.C. — "A Proof for the Queuing Formula: L = λW" (1961).
  Gives the math (queue length vs. wait time vs. throughput) to explain the sync-vs-async JMeter benchmark results referenced in the top-level README.

## Priority

If reading only two: SEDA (architecture) + Whisper paper (core dependency).
Outbox/consistency reading matters most once the dual-write gap is actually fixed.
