## Create POT (Translation Template) File from Clojure source files

```clj
;; clojure -X:mr-worldwide.build.pot/build-pot!
;; clojure -X:mr-worldwide.build.pot/build-pot! '{:source-paths ["src"]}'
(mr-worldwide.build.pot/build-pot! config)

{:pot-filename     "target/mr-worldwide/strings.pot"
 :source-paths     []
 ;; TODO -- see if we actually still need this.
 :overrides [{:file "/src/metabase/analyze/fingerprint/fingerprinters.clj"
              :message "Error generating fingerprint for {0}"}]}
```

## Create EDN and JSON Artifacts from PO Files

```clj
(mr-worldwide.build.artifacts/create-artifacts! config)

{:po-files-directory    []
 :target-directory      "resources/mr-worldwide"
 :clj-target-directoy   "<target-directory>/clj"
 :cljs-target-directory "<target-directory>/cljs"
 :config-filename       "<target-directory>/config.edn"
 :packages              ["mr_worldwide"]
 :bundle                "mr_worldwide.Messages"}
```
