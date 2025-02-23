(ns mr-worldwide.build.artifacts.clj
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [mr-worldwide.build.common :as i18n]
   [mr-worldwide.build.util :as u])
  (:import
   (java.io FileOutputStream OutputStreamWriter)
   (java.nio.charset StandardCharsets)))

(set! *warn-on-reflection* true)

(defn- clj-message? [{:keys [source-references], :as _message}]
  (some (fn [path]
          (re-find #"\.cljc?(?::\d+)?$" path))
        source-references))

(def ^:private apostrophe-regex
  "Regex that matches incorrectly escaped apostrophe characters.

  Matches on a single apostrophe surrounded by any letter, number, space, or diacritical character (chars with accents
  like é) and is case-insensitive."
  #"(?<![^a-zA-Z0-9\s\u00C0-\u017F])'(?![^a-zA-Z0-9\s\u00C0-\u017F])")

(defn- fix-unescaped-apostrophes [message]
  (let [escape-fn #(str/replace % apostrophe-regex "''")]
    (if (:plural? message)
      (update message :str-plural #(map escape-fn %))
      (update message :str escape-fn))))

(defn- messages->edn
  [messages]
  (eduction
   (filter clj-message?)
   (map fix-unescaped-apostrophes)
   i18n/log-message-count-xform
   messages))

(defn- target-filename [{:keys [clj-target-directory], :as _config} locale]
  (u/filename clj-target-directory (format "%s.edn" locale)))

(defn- write-edn-file! [po-contents target-file]
  (log/debug "Write EDN file")
  (with-open [os (FileOutputStream. (io/file target-file))
              w  (OutputStreamWriter. os StandardCharsets/UTF_8)]
    (.write w "{\n")
    (.write w ":headers\n")
    (.write w (pr-str (:headers po-contents)))
    (.write w "\n\n")
    (.write w ":messages\n")
    (.write w "{\n")
    (doseq [{msg-id :id, msg-str :str, msg-str-plural :str-plural}
            (messages->edn (:messages po-contents))
            :let [msg-strs (or msg-str-plural [msg-str])]]
      (.write w (pr-str msg-id))
      (.write w "\n")
      (when msg-str-plural (.write w "["))
      (doseq [msg (butlast msg-strs)]
        (.write w (pr-str msg))
        (.write w " "))
      (.write w (pr-str (last msg-strs)))
      (when msg-str-plural (.write w "]"))
      (.write w "\n\n"))
    (.write w "}\n")
    (.write w "}\n")))

(defn create-artifact-for-locale!
  "Create an artifact with translated strings for `locale` for backend (Clojure) usage."
  [{:keys [clj-target-directory], :as config} locale]
  {:pre [(some? clj-target-directory)]}
  (let [target-file (target-filename config locale)]
    (log/infof "Create CLJ artifact %s from %s" target-file (i18n/locale-source-po-filename config locale))
    (u/create-directory-unless-exists! clj-target-directory)
    (u/delete-file-if-exists! target-file)
    (write-edn-file! (i18n/po-contents config locale) target-file)
    (u/assert-file-exists target-file)))
