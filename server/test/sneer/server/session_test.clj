(ns sneer.server.session-test
  (:require [midje.sweet :refer :all]
            [sneer.tuple.jdbc-database :refer [create-sqlite-db]]
            [sneer.tuple-base-provider :refer :all]
            [sneer.rx :refer [subscribe-on-io]]
            [sneer.test-util :refer [<!!? observable->chan]]
            [sneer.admin :refer [new-sneer-admin-over-db]]
            [sneer.keys :refer [->puk]]
            [sneer.server.network :as network]
            [sneer.server.simulated-network :as sim-network])
  (:import [sneer Sneer Conversation Party Contact]))

(defn- ->chan [obs]
  (observable->chan (subscribe-on-io obs)))

(facts "About Sessions"
  (with-open [neide-db (create-sqlite-db)
              maico-db (create-sqlite-db)
              neide-admin (new-sneer-admin-over-db neide-db)
              maico-admin (new-sneer-admin-over-db maico-db)]
    (let [neide-puk (.. neide-admin privateKey publicKey)
          maico-puk (.. maico-admin privateKey publicKey)
          neide-sneer (.sneer neide-admin)
          maico-sneer (.sneer maico-admin)
          neide-tuple-base (tuple-base-of neide-admin)
          maico-tuple-base (tuple-base-of maico-admin)
          neide (.produceContact maico-sneer "neide" (.produceParty maico-sneer neide-puk) nil)
          maico (.produceContact neide-sneer "maico" (.produceParty neide-sneer maico-puk) nil)
          n->m (.. neide-sneer conversations (withContact maico))
          m->n (.. maico-sneer conversations (withContact neide))

          sim (sim-network/start)]
      (network/connect sim neide-puk neide-tuple-base)
      (network/connect sim maico-puk maico-tuple-base)

      (fact "Communication is working"
            (.sendMessage n->m "Hello")

            (let [messages (->chan (.messages m->n))]
              (<!!? messages)                               ; Skip history replay :(
              (.size (<!!? messages)) => 1))

      #_(fact "Neide sees her own messages in the session"
            (let [session (.startSession n->m)
                  ]
              (.send session "some payload")
              (.. session messages toBlocking first payload) => "some payload"
              ))

      #_(fact "..."
        (<!!? notifications) => notification-empty?))))
