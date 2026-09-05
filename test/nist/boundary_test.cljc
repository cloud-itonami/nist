(ns nist.boundary-test
  "Invariants that `nist.murakumo-test` structurally cannot see.

  That suite deliberately introspects `cell-specs` — it derives its expectation
  from the same data it checks, so it stays green when the data itself is
  narrowed. Removing a baseline gate from `common-gates`, moving a collection
  out of this actor's NSID namespace, or dropping the `^` anchor off the
  `did:web:` strip in `safe-rkey` are all invisible to it.

  These tests carry their own literals on purpose. When a literal here has to
  change, that change is the decision — it should cost a diff, not pass in
  silence."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [nist.murakumo :as m]))

;; ---------------------------------------------------------------------------
;; Identity: the DID and the NSID base the README calls compatibility identities
;; ---------------------------------------------------------------------------

(def expected-actor-did "did:web:nist.etzhayyim.com")
(def expected-nsid-base "com.etzhayyim.nist.")

(deftest actor-identity-is-pinned
  (testing "the actor DID the README names as a compatibility identity"
    (is (= expected-actor-did m/actor-did)))
  (testing "collection/1 builds names under this actor's own NSID namespace"
    (is (= (str expected-nsid-base "health") (m/collection "health")))))

(deftest every-collection-lives-under-this-actors-namespace
  (doseq [[cell-key spec] m/cell-specs
          coll (:collections spec)]
    (is (str/starts-with? coll expected-nsid-base)
        (str cell-key ": " coll " is outside " expected-nsid-base))
    (is (seq (subs coll (count expected-nsid-base)))
        (str cell-key ": " coll " has an empty leaf segment"))))

;; ---------------------------------------------------------------------------
;; The baseline gate set — the introspective suite reads this from the spec,
;; so narrowing it there is free. Here it costs.
;; ---------------------------------------------------------------------------

(def expected-baseline-gates
  #{:council-charter-attestation
    :no-platform-held-key-baseline
    :no-probing-baseline
    :murakumo-only-inference-baseline
    :did-primary-baseline
    :append-only-gate-baseline
    :kotoba-only-substrate-baseline})

(deftest common-gates-is-exactly-the-seven-baselines
  (is (= expected-baseline-gates (set m/common-gates)))
  (testing "no duplicates hiding a removal behind the count"
    (is (= (count m/common-gates) (count (set m/common-gates))))))

(deftest every-cell-requires-every-baseline-gate
  (doseq [[cell-key spec] m/cell-specs]
    (is (= expected-baseline-gates (set (:required-gates spec)))
        (str cell-key " does not require exactly the baseline gate set"))))

;; ---------------------------------------------------------------------------
;; gate-value: an explicitly false attestation is NOT an attestation
;; ---------------------------------------------------------------------------

(deftest a-false-attestation-does-not-satisfy-a-gate
  (testing "false under a keyword key"
    (is (nil? (m/gate-value {:council-charter-attestation false}
                            :council-charter-attestation))))
  (testing "false under a string key"
    (is (nil? (m/gate-value {"council-charter-attestation" false}
                            :council-charter-attestation))))
  (testing "and it still counts as missing on a real spec"
    (let [spec (first (vals m/cell-specs))
          all-false (into {} (map (fn [g] [g false])) (:required-gates spec))]
      (is (= (vec (:required-gates spec)) (m/missing-gates spec all-false))))))

;; ---------------------------------------------------------------------------
;; A blocked cell emits nothing — not an empty effect list beside a full plan
;; ---------------------------------------------------------------------------

(deftest a-blocked-plan-carries-no-planned-records
  (doseq [cell-key (keys m/cell-specs)]
    (let [plan (m/cell-plan cell-key {:request-id "req-blocked"})]
      (is (= :blocked (:status plan)))
      (is (not (contains? plan :records))
          (str cell-key ": a blocked plan leaked :records"))
      (is (empty? (:effects plan))))))

(deftest one-missing-gate-is-enough-to-block
  (let [[cell-key spec] (first m/cell-specs)
        gates (:required-gates spec)
        all-but-one (into {} (map (fn [g] [g "attested"])) (rest gates))
        plan (m/cell-plan cell-key {:attestations all-but-one
                                    :request-id "req-partial"})]
    (is (= :blocked (:status plan)))
    (is (= [(first gates)] (:missing-gates plan)))
    (is (empty? (:effects plan)))))

;; ---------------------------------------------------------------------------
;; safe-rkey — untested until now, and it is the only sanitiser between
;; caller-supplied text and an emitted record key
;; ---------------------------------------------------------------------------

(deftest safe-rkey-strips-only-a-leading-did-web-prefix
  (is (= "nist.etzhayyim.com" (m/safe-rkey "did:web:nist.etzhayyim.com")))
  (testing "a did:web: that is not at the start is sanitised, not stripped"
    (is (= "a-did-web-b" (m/safe-rkey "a-did:web:b")))))

(deftest safe-rkey-replaces-every-character-outside-the-rkey-charset
  (testing "the permitted charset survives untouched"
    (is (= "Aa0._~-" (m/safe-rkey "Aa0._~-"))))
  (testing "everything else becomes a hyphen, globally not just once"
    (is (= "a-b-c-d" (m/safe-rkey "a/b c:d")))
    (is (= "-----" (m/safe-rkey "!@#$%")))))

(deftest safe-rkey-never-returns-a-blank-key
  (is (= "unknown" (m/safe-rkey "")))
  (is (= "unknown" (m/safe-rkey nil)))
  (testing "non-string input is coerced rather than thrown on"
    (is (= "42" (m/safe-rkey 42)))))

(deftest records-for-sanitises-a-caller-supplied-rkey
  (let [[_ spec] (first (filter (fn [[_ s]] (= 1 (count (:collections s))))
                                m/cell-specs))
        recs (m/records-for spec {:record {:rkey "did:web:x/../y z"}})
        rkey (:rkey (first recs))]
    (is (= "x-..-y-z" rkey)
        "records-for must route the caller's rkey through safe-rkey")))

;; ---------------------------------------------------------------------------
;; Record shape: a scaffold record must say it is a scaffold
;; ---------------------------------------------------------------------------

(deftest planned-records-default-their-type-to-their-collection
  (doseq [[cell-key spec] m/cell-specs
          {:keys [collection record]} (m/records-for spec {:request-id "req-1"})]
    (is (= collection (:$type record))
        (str cell-key ": $type drifted from the collection it is written to"))))

(deftest planned-records-declare-their-constitutional-status
  (doseq [[cell-key spec] m/cell-specs
          {:keys [record]} (m/records-for spec {:request-id "req-1"})]
    (is (= "attested-plan" (:constitutionalStatus record))
        (str cell-key ": a scaffold record stopped saying it is a plan"))
    (is (= "cljc-migration-scaffold" (:actorBoundary record)))))

;; ---------------------------------------------------------------------------
;; Cells do not collide with each other
;; ---------------------------------------------------------------------------

(deftest cells-do-not-share-a-legacy-cell-or-a-collection
  (let [legacy (map (comp :legacy-cell val) m/cell-specs)
        colls (mapcat (comp :collections val) m/cell-specs)]
    (is (= (count legacy) (count (set legacy)))
        "two cells claim the same legacy cell")
    (is (= (count colls) (count (set colls)))
        "two cells write to the same collection")))

(deftest every-cell-spec-is-fully-populated
  (doseq [[cell-key spec] m/cell-specs]
    (is (= :event (:phase spec)) (str cell-key ": phase"))
    (is (and (string? (:legacy-cell spec)) (not (str/blank? (:legacy-cell spec))))
        (str cell-key ": legacy-cell"))
    (is (and (string? (:murakumo-node spec)) (not (str/blank? (:murakumo-node spec))))
        (str cell-key ": murakumo-node"))
    (is (seq (:collections spec)) (str cell-key ": collections"))))
