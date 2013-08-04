(ns rekrvn.modules.voveri.tests.engine-test
  (:require [clojure.test :refer :all])
  (:require [rekrvn.modules.voveri.engine :refer :all]))

(defn- noop [])

(deftest in-phase-test
  (is (= (:error (in-phase initial-game-state :foo (noop)))
         :wrong-phase))
  (is (= (in-phase initial-game-state :inactive :success)
         :success)))

