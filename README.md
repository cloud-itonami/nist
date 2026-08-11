# nist — NIST security-posture actor

**Repository**: `cloud-itonami/nist`

Operational NIST CSF 2.0, CMMC L2, and community-profile coordination for the
Itonami service plane. The historical `nist.etzhayyim.com` DID, domain, and
`com.etzhayyim.*` protocol namespaces remain compatibility identities.

Reusable NIST primitives remain separate in `kotoba-lang` repositories such as
`org-nist-sha2`; this repository owns assessment, gap-analysis, and posture
workflows rather than the underlying standards implementation.

Run the deterministic suite with `clojure -M:test`.
