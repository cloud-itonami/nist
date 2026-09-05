(ns run-tests
  "JVM-free runner for the nist actor contract (CLAUDE.md runtime order puts
  nbb above the JVM). `clojure -M:test` runs the same .cljc suites."
  (:require [clojure.test :as t]
            [nist.murakumo-test]
            [nist.boundary-test]))

(defmethod t/report [:cljs.test/default :end-run-tests] [m]
  (if (or (pos? (:fail m)) (pos? (:error m)))
    (do (println "FAIL") (js/process.exit 1))
    (println "nist actor contract: all green")))

(t/run-tests 'nist.murakumo-test 'nist.boundary-test)
