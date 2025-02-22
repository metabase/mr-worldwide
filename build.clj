(ns build
  (:refer-clojure :exclude [compile])
  (:require
   [clojure.java.shell :as sh]
   [clojure.string :as str]
   [clojure.tools.build.api :as b]
   [deps-deploy.deps-deploy :as dd]))

(set! *warn-on-reflection* true)

(def github-url "https://github.com/metabase/mr-worldwide")
(def scm-url    "git@github.com:metabase/mr-worldwide.git")

(def major-minor-version "1.0")

(defn sh [& args]
  (let [{:keys [exit out]} (apply sh/sh args)]
    (assert (zero? exit))
    (str/trim out)))

(defn commit-number []
  (or (-> (sh/sh "git" "rev-list" "HEAD" "--count")
          :out
          str/trim
          parse-long)
      "9999-SNAPSHOT"))

(def sha
  (or (not-empty (System/getenv "GITHUB_SHA"))
      (not-empty (-> (sh/sh "git" "rev-parse" "HEAD")
                     :out
                     str/trim))))

(def version (str major-minor-version \. (commit-number)))
(def target "target")
(def class-dir (format "%s/classes" target))

(defn lib [project]
  (case project
    mr-worldwide       'io.github.metabase/mr-worldwide
    mr-worldwide.build 'io.github.metabase/mr-worldwide.build))

(defn jar-file [project]
  (format "target/%s-%s.jar" (name (lib project)) version))

(defn basis [project]
  (b/create-basis
   {:project
    (case project
      mr-worldwide       "mr-worldwide/deps.edn"
      mr-worldwide.build "mr-worldwide.build/deps.edn")}))

(def pom-template
  [[:description "Mr. Worldwide, Mr. 305!"]
   [:url github-url]
   [:licenses
    [:license
     [:name "Eclipse Public License"]
     [:url "http://www.eclipse.org/legal/epl-v20.html"]]]
   [:developers
    [:developer
     [:name "Cam Saul"]]]
   [:scm
    [:url github-url]
    [:connection (str "scm:git:" scm-url)]
    [:developerConnection (str "scm:git:" scm-url)]
    [:tag sha]]])

(defn default-options [project]
  {:lib       (lib project)
   :version   version
   :jar-file  (jar-file project)
   :basis     (basis project)
   :class-dir class-dir
   :target    target
   :src-dirs  ["src"]
   :pom-data  pom-template})

(defn clean [_]
  (b/delete {:path target}))

(defn jar [{:keys [project], :as opts}]
  (println "\nStarting to build a JAR...")
  (println "\tWriting pom.xml...")
  (b/write-pom (merge (default-options project) opts))
  (println "\tCopying source...")
  (b/copy-dir {:src-dirs   ["src" "resources"]
               :target-dir class-dir})
  (printf "\tBuilding %s...\n" (jar-file project))
  (b/jar {:class-dir class-dir
          :jar-file  (jar-file project)})
  (println "Done! 🦜"))

(defn deploy [{:keys [project], :as opts}]
  (let [opts (merge (default-options project) opts)]
    (printf "Deploying %s...\n" (jar-file project))
    (dd/deploy {:installer :remote
                :artifact  (b/resolve-path (jar-file project))
                :pom-file  (b/pom-path (select-keys opts [:lib :class-dir]))})
    (println "Deployed! 🦅")))
