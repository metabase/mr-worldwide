(ns mr-worldwide.build.artifacts.cljs-test
  (:require
   [clojure.test :refer :all]
   [mr-worldwide.build.artifacts.cljs :as cljs]
   [mr-worldwide.build.artifacts.test-common :as test-common]))

(deftest ^:parallel ->ttag-reference-test
  (is (= "${ 0 } schemas"
         (#'cljs/->ttag-reference "{0} schemas"))))

(deftest ^:parallel ->i18n-map-test
  (is (= {:charset      "utf-8"
          :headers      {"mime-version"              "1.0"
                         "content-type"              "text/plain; charset=UTF-8"
                         "content-transfer-encoding" "8bit"
                         "x-generator"               "POEditor.com"
                         "project-id-version"        "Metabase"
                         "language"                  "es"
                         "plural-forms"              "nplurals=2; plural=(n != 1);"}
          :translations {""
                         {"No table description yet"
                          {:msgstr ["No hay una descripción de la tabla"]}

                          "Count of ${ 0 }"
                          {:msgstr ["Número de ${ 0 }"]}

                          "${ 0 } Queryable Table"
                          {:msgid_plural "{0} Queryable Tables"
                           :msgstr       ["${ 0 } Tabla Consultable" "${ 0 } Tablas consultables"]}

                          "${ 0 } metric"
                          {:msgid_plural "{0} metrics"
                           :msgstr       ["${ 0 } metrik" ""]}}}}
         (#'cljs/->i18n-map test-common/po-contents))))
