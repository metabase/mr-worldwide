(ns mr-worldwide.test-util
  (:require
   [clojure.test :as t]
   [mr-worldwide.core :as i18n]
   [mr-worldwide.impl :as i18n.impl]))

;;; TODO -- this could be made thread-safe pretty easily if we made [[i18n.impl/translations]] dynamic
(defn do-with-mock-i18n-bundles
  "Impl for the [[with-mock-i18n-bundles]] macro."
  [bundles thunk]
  (t/testing (format "\nwith mock i18n bundles %s\n" (pr-str bundles))
    (let [locale->bundle (into {} (for [[locale-name bundle] bundles]
                                    [(i18n/locale locale-name) bundle]))]
      (binding [i18n.impl/*translations* (comp locale->bundle i18n/locale)]
        (thunk)))))

(defmacro with-mock-i18n-bundles
  "Mock the i18n resource bundles for the duration of `body`.

    (with-mock-i18n-bundles {\"es\"    {:messages {\"Your database has been added!\"
                                                   [\"¡Tu base de datos ha sido añadida!\"]}}
                             \"es-MX\" {:messages {\"I''m good thanks\"
                                                   [\"Está bien, gracias\"]}}}
      (translate \"es-MX\" \"Your database has been added!\"))
    ;; -> \"¡Tu base de datos ha sido añadida!\""
  [bundles & body]
  `(do-with-mock-i18n-bundles ~bundles (fn [] ~@body)))
