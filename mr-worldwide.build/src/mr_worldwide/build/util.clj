(ns mr-worldwide.build.util
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.logging :as log])
  (:import
   (java.io File)
   (org.apache.commons.io FileUtils)))

(set! *warn-on-reflection* true)

(defn file-exists?
  "Does a file or directory with `filename` exist?"
  [filename]
  (when filename
    (.exists (io/file filename))))

(defn assert-file-exists
  "If file with `filename` exists, return `filename` as is; otherwise, throw Exception."
  ^String [filename & [message]]
  (when-not (file-exists? filename)
    (throw (ex-info (format "File %s does not exist. %s" (pr-str filename) (or message ""))
                    {:filename filename})))
  (str filename))

(defn filename
  "Create a filename path String by joining path components:

    (filename \"usr\" \"cam\" \".emacs.d\" \"init.el\")
    ;; -> \"usr/cam/.emacs.d/init.el\""
  [& path-components]
  (str/join File/separatorChar path-components))

(defn delete-file-if-exists!
  "Delete a file or directory (recursively) if it exists."
  ([filename]
   (log/debugf "Delete %s if exists" filename)
   (if (file-exists? filename)
     (let [file (io/file filename)]
       (if (.isDirectory file)
         (FileUtils/deleteDirectory file)
         (.delete file))
       (log/debugf "Deleted %s." filename))
     (log/debugf "Don't need to delete %s, file does not exist." filename))
   (assert (not (file-exists? filename))))

  ([file & more]
   (doseq [file (cons file more)]
     (delete-file-if-exists! file))))

(defn create-directory-unless-exists!
  "Create a directory if it does not already exist. Returns `dir`."
  ^String [dir]
  (log/debugf "Create directory %s if it does not exist" dir)
  (if (file-exists? dir)
    (log/debugf "%s already exists." dir)
    (do
      (log/debugf "Create directory %s" dir)
      (.mkdirs (io/file dir))))
  dir)
