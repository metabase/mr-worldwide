(ns mr-worldwide.build.enumerate
  "Enumerate and create pot file from the backend worktree of metabase. Look through all of our source paths for calls
  to `trs`, `deferred-trs`, etc. to find strings we need in our pot file (po template). Use grasp to find forms by the
  `::translate` spec. These forms come back with metadata indicating file and line/column. We look at the form to pick
  out either the string literal or a call to `str` concatenating several string literals. Main function will write the
  pot file and exit with 0 if all forms could be analyzed else returns 1."
  (:require
   [clojure.java.io :as io]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [grasp.api :as g])
  (:import
   (org.fedorahosted.tennera.jgettext Catalog HeaderFields Message PoWriter)))

(set! *warn-on-reflection* true)

(defn- strip-roots
  [config path]
  (str/replace path
               (re-pattern (str/join "|" (map #(str "file:" % "/") (:source-paths config))))
               ""))

(def ^:private translation-vars
  "Vars that are looked for for translations strings"
  #{'mr-worldwide.core/trs
    'mr-worldwide.core/tru
    'mr-worldwide.core/deferred-trs
    'mr-worldwide.core/deferred-tru
    'mr-worldwide.core/trsn
    'mr-worldwide.core/trun
    'mr-worldwide.core/deferred-trsn
    'mr-worldwide.core/deferred-trun})

(def ^:private plural-translation-macro-names
  #{"trsn" "trun" "deferred-trsn" "deferred-trun"})

(s/def ::translate (s/and
                    (complement vector?)
                    (s/cat :translate-symbol (fn [x]
                                               (and (symbol? x)
                                                    (translation-vars (g/resolve-symbol x))))
                           :args (s/+ any?))))

(defn- form->messages
  "Function that turns a form into a map containing the translation string, and optional plural translation string
  if one is present.

  Handles string literals and calls to `str` on string literals. Returns nil if unable to recognize translation string.

  (form->string-for-translation (tru \"Foo {0}\"))                 -> {:message \"Foo {0}\"}
  (form->string-for-translation (tru (str \"Foo {0} \" \"Bar\"))   -> {:message \"Foo {0} Bar\"}
  (form->string-for-translation (trun \"{0} Foo\" \"{0} Foos\" n)) -> {:message \"{0} Foo\" :message-pl \"{0} Foos\"}"
  [form]
  (let [macro-name     (name (first form))
        i18n-string    (second form)
        pl-i18n-string (when (plural-translation-macro-names macro-name) (nth form 2))]
    (when-let [message (cond (string? i18n-string)
                             i18n-string
                             ;; Concatenate (str ...) forms)
                             (and (seqable? i18n-string) (every? string? (rest i18n-string)))
                             (apply str (rest i18n-string)))]
      (merge {:message message}
             (when (string? pl-i18n-string)
               {:message-pl pl-i18n-string})))))

(defn- analyze-translations
  "Takes roots to grasp returning a map of :file, :line, :original (the original form), and :message. If identifying the
  message failed message will be nil and this should be filtered out of further processing."
  [{:keys [source-paths], :as config}]
  (map (fn [result]
         (let [{:keys [line _col uri]} (meta result)]
           (merge
            {:file     (strip-roots config uri)
             :line     line
             :original result}
            (form->messages result))))
       (g/grasp source-paths ::translate)))

(defn- group-results-by-string
  "Each string can be in the pot file once and only once (a string can only have a single translation). Want all
  filenames collapsed into a list for each message."
  [config results]
  (->> results
       (concat (:overrides config))
       (sort-by :file)
       (group-by :message)
       (sort-by (comp :file first val))
       (map (fn [[string originals]]
              {:message    string
               :message-pl (->> originals (filter :message-pl) first :message-pl)
               :files      (map #(select-keys % [:file :line]) originals)}))))

(defn- usage->Message
  "Return a `Message` instance suitable for adding to a `Catalog`"
  ^Message [{:keys [message message-pl files]}]
  (let [msg (Message.)]
    (.setMsgid msg message)
    (when message-pl (.setMsgidPlural msg message-pl))
    (doseq [file files]
      (if-let [line (:line file)]
        (.addSourceReference msg (:file file) line)
        (.addSourceReference msg (:file file))))
    msg))

(defn- header
  "Headers are just another message. Create one with our info."
  ^Message []
  (let [hv (HeaderFields.)
        now (.format (java.text.SimpleDateFormat. "yyyy-MM-dd HH:mmZ")
                     (java.util.Date.))]
    (doseq [[prop value] [[HeaderFields/KEY_ProjectIdVersion "1.0"]
                          [HeaderFields/KEY_ReportMsgidBugsTo "docs@metabase.com"]
                          [HeaderFields/KEY_PotCreationDate now]
                          [HeaderFields/KEY_MimeVersion "1.0"]
                          [HeaderFields/KEY_ContentType "text/plain; charset=UTF-8"]
                          [HeaderFields/KEY_ContentTransferEncoding "8bit"]]]
      (.setValue hv prop value))
    (let [message (.unwrap hv)]
      (doseq [comment ["Copyright (C) 2022 Metabase <docs@metabase.com>"
                       "This file is distributed under the same license as the Metabase package"]]
        (.addComment message comment))
      message)))

(defn processed->catalog
  "Takes the grouped usages and returns a catalog."
  ^Catalog [usages]
  (let [header (header)
        catalog (Catalog. true #_is-pot)]
    (.addMessage catalog header)
    (doseq [usage usages]
      (.addMessage catalog (usage->Message usage)))
    catalog))

(defn- create-pot-file!
  "String sources and an output filename. Writes the pot file of translation strings found in sources and returns a map
  of number of valid usages, number of distinct translation strings, and the bad forms that could not be identified."
  [{:keys [pot-filename], :as config}]
  (let [analyzed   (analyze-translations config)
        valid?     (comp string? :message)
        bad-forms  (remove valid? analyzed)
        good-forms (filter valid? analyzed)
        grouped    (group-results-by-string config good-forms)]
    (with-open [writer (io/writer pot-filename)]
      (let [po-writer (PoWriter.)
            catalog   (processed->catalog grouped)]
        (.write po-writer catalog writer)))
    (println "Created pot file at " pot-filename)
    {:valid-usages (count good-forms)
     :entry-count  (count grouped)
     :bad-forms    bad-forms}))

(defn enumerate
  "Entrypoint for creating a backend pot file. Exits with 0 if all forms were processed correctly, exits with 1 if one
  or more forms were found that it could not process."
  [{:keys [pot-filename], :as config}]
  (when (str/blank? pot-filename)
    (throw (ex-info (str "Please provide a filename argument. e.g.: "
                         \newline
                         "  clj -X:build mr-worldwide.build.enumerate/enumerate :pot-filename '\"metabase.pot\"'")
                    {:config config})))
  (let [{:keys [valid-usages entry-count bad-forms]} (create-pot-file! config)]
    (printf "Found %d forms for translations\n" valid-usages)
    (printf "Grouped into %d distinct pot entries\n" entry-count)
    (when (seq bad-forms)
      (throw (ex-info (format "Found %d forms that could not be analyzed" (count bad-forms))
                      {:config    config
                       :bad-forms bad-forms})))
    (println "Done.")))
