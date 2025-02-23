(ns mr-worldwide.build.artifacts.cljs
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [mr-worldwide.build.common :as i18n]
   [mr-worldwide.build.util :as u])
  (:import
   (java.io FileOutputStream OutputStreamWriter)
   (java.nio.charset StandardCharsets)))

(set! *warn-on-reflection* true)

(defn- js-or-cljs-message?
  "Whether this i18n `message` comes from a frontend source file."
  [{:keys [source-references], :as _message}]
  (some (fn [path]
          ;; cljc, cljs, js, ts, jsx, tsx with optional line number
          (re-find #"\.(?:(?:clj[cs])|(?:[jt]sx?))(?::\d+)?$" path))
        source-references))

(defn- ->ttag-reference
  "Replace an xgettext `{0}` style reference with a ttag `${ 0 }` style reference."
  [message-id]
  {:pre [(string? message-id)]}
  (str/replace message-id #"\{\s*(\d+)\s*\}" "\\${ $1 }"))

(defn- ->translations-map [messages]
  {"" (into {}
            (comp
             ;; filter out i18n messages that aren't used on the FE client
             (filter js-or-cljs-message?)
             i18n/log-message-count-xform
             (map (fn [message]
                    [(->ttag-reference (:id message))
                     (if (:plural? message)
                       {:msgid_plural (:id-plural message)
                        :msgstr       (map ->ttag-reference (:str-plural message))}
                       {:msgstr [(->ttag-reference (:str message))]})])))
            messages)})

(defn- ->i18n-map
  "Convert the contents of a `.po` file to map format used in the frontend client."
  [po-contents]
  {:charset      "utf-8"
   :headers      (into {} (for [[k v] (:headers po-contents)]
                            [(.toLowerCase (str k) java.util.Locale/ENGLISH) v]))
   :translations (->translations-map (:messages po-contents))})

(defn- i18n-map [config locale]
  (->i18n-map (i18n/po-contents config locale)))

(defn- target-filename [{:keys [cljs-target-directory], :as _config} locale]
  {:pre [(some? cljs-target-directory)]}
  (u/filename cljs-target-directory (format "%s.json" (str/replace locale #"-" "_"))))

(defn create-artifact-for-locale!
  "Create an artifact with translated strings for `locale` for frontend (JS) usage."
  [{:keys [cljs-target-directory], :as config} locale]
  {:pre [(some? cljs-target-directory)]}
  (let [target-file (target-filename config locale)]
    (log/infof "Create frontend artifact %s from %s" target-file (i18n/locale-source-po-filename config locale))
    (u/create-directory-unless-exists! cljs-target-directory)
    (u/delete-file-if-exists! target-file)
    (log/debug "Write JSON")
    (with-open [os (FileOutputStream. (io/file target-file))
                w  (OutputStreamWriter. os StandardCharsets/UTF_8)]
      (json/generate-stream (i18n-map config locale) w))
    (u/assert-file-exists target-file)))
