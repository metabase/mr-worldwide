(ns mr-worldwide.build.util
  (:require
   [clojure.string :as str])
  (:import
   (java.io File)
   (org.apache.commons.io FileUtils)))

(set! *warn-on-reflection* true)

(defn file-exists?
  "Does a file or directory with `filename` exist?"
  [^String filename]
  (when filename
    (.exists (File. filename))))

(defn assert-file-exists
  "If file with `filename` exists, return `filename` as is; otherwise, throw Exception."
  ^String [filename & [message]]
  (when-not (file-exists? filename)
    (throw (ex-info (format "File %s does not exist. %s" (pr-str filename) (or message "")) {:filename filename})))
  (str filename))

(defn filename
  "Create a filename path String by joining path components:

    (filename \"usr\" \"cam\" \".emacs.d\" \"init.el\")
    ;; -> \"usr/cam/.emacs.d/init.el\""
  [& path-components]
  (str/join File/separatorChar path-components))

(defn delete-file-if-exists!
  "Delete a file or directory (recursively) if it exists."
  ([^String filename]
   (printf "Delete %s if exists\n" filename)
   (if (file-exists? filename)
     (let [file (File. filename)]
       (if (.isDirectory file)
         (FileUtils/deleteDirectory file)
         (.delete file))
       (printf "Deleted %s.\n" filename))
     (printf "Don't need to delete %s, file does not exist.]\n" filename))
   (assert (not (file-exists? filename))))

  ([file & more]
   (dorun (map delete-file-if-exists! (cons file more)))))

(defn create-directory-unless-exists!
  "Create a directory if it does not already exist. Returns `dir`."
  ^String [^String dir]
  (printf "Create directory %s if it does not exist\n" dir)
  (if (file-exists? dir)
    (printf "%s already exists.\n" dir)
    (do
      (printf "Create directory %s\n" dir)
      (.mkdirs (File. dir))))
  dir)
