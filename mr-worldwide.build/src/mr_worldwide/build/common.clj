(ns mr-worldwide.build.common
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [mr-worldwide.build.util :as u])
  (:import
   (org.fedorahosted.tennera.jgettext Catalog HeaderFields Message PoParser)))

(set! *warn-on-reflection* true)

(defn locales
  "Set of all locales for which we have i18n bundles.

    (locales {:po-files-directory \"/home/cam/metabase/locales\"})
    ;; =>
    #{\"nl\" \"pt\" \"zh\" \"tr\" \"it\" \"fa\" ...}"
  [{:keys [po-files-directory], :as _config}]
  {:pre [(some? po-files-directory)]}
  (into
   (sorted-set)
   (for [^java.io.File file (.listFiles (io/file po-files-directory))
         :let               [file-name (.getName file)]
         :when              (str/ends-with? file-name ".po")]
     (str/replace file-name #"\.po$" ""))))

(defn locale-source-po-filename
  "E.g.

  (locale-source-po-filename {:po-files-directory \"/home/cam/metabase/locales\"} \"fr\")
  ;; =>
  \"/home/cam/metabase/locales/fr.po\""
  [{:keys [po-files-directory], :as _config} locale]
  {:pre [(some? po-files-directory)]}
  (u/filename po-files-directory (format "%s.po" locale)))

;; see https://github.com/zanata/jgettext/tree/master/src/main/java/org/fedorahosted/tennera/jgettext

(defn- catalog ^Catalog [config locale]
  (let [parser (PoParser.)]
    (.parseCatalog parser (io/file (locale-source-po-filename config locale)))))

(defn- po-headers [config locale]
  (when-let [^Message message (.locateHeader (catalog config locale))]
    (let [header-fields (HeaderFields/wrap (.getMsgstr message))]
      (into {} (for [^String k (.getKeys header-fields)]
                 [k (.getValue header-fields k)])))))

(defn- po-messages-seq [config locale]
  (for [^Message message (iterator-seq (.iterator (catalog config locale)))
        ;; remove any empty translations
        :when            (not (str/blank? (.getMsgid message)))]
    {:id                (.getMsgid message)
     :id-plural         (.getMsgidPlural message)
     :str               (.getMsgstr message)
     :str-plural        (seq (.getMsgstrPlural message))
     :fuzzy?            (.isFuzzy message)
     :plural?           (.isPlural message)
     :source-references (seq (remove str/blank? (.getSourceReferences message)))
     :comment           (.getMsgctxt message)}))

(defn po-contents
  "Contents of the PO file for a `locale`."
  [config locale]
  {:headers  (po-headers config locale)
   :messages (po-messages-seq config locale)})

(defn log-message-count-xform
  "Transducer that prints a count of how many translation strings we process/write."
  [rf]
  (let [num-messages (volatile! 0)]
    (fn
      ([]
       (rf))
      ([result]
       (log/infof "Wrote %d messages." @num-messages)
       (rf result))
      ([result message]
       (vswap! num-messages inc)
       (rf result message)))))
