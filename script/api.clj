(ns api
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [clojure.tools.cli :as cli]
            [util]
            [babashka.curl :as curl]))

(defn read-auth
  [auth-file]
  (-> auth-file
      slurp
      edn/read-string))

(defn store-auth
  [auth-file year session-key]
  (let [current-auth (if (fs/exists? auth-file)
                       (-> auth-file
                           slurp
                           edn/read-string)
                       {})]
    (-> current-auth
        (assoc year session-key)
        pr-str
        (#(spit auth-file %)))))

(defn fetch-auth
  [auth-file year]
  (get (read-auth auth-file) (str year)))

(defn download-input
  [auth-file input-dir year day]
  (if-let [session (fetch-auth auth-file year)]
    (let [base-path (fs/path input-dir year)
          input-filename (str day ".txt")
          input-path (fs/path base-path input-filename)]
      (if (fs/exists? input-path)
        (println "Input for year" year "and" day "has already been fetched")
        (let [url (format "https://adventofcode.com/%s/day/%s/input" year day)
              _ (println "Fetching:" url)
              input (curl/get url {:headers {"Cookie" (str "session=" session)}})]
          (when-not (fs/exists? base-path)
            (fs/create-dir base-path))
          (spit (str input-path) (:body input))
          (println "Input for year" year "and" day "successfully pulled"))))
    (println "Missing auth for year:" year)))

(def download-input-params
  [["-y" "--year YEAR" "The year this input is associated with"
    :default (str (util/get-current-year))
    :validate [parse-long "Year must be an integer"]]
   ["-d" "--day DAY" "The day this input is associated with"
    :default (str (util/get-current-day))
    :validate [parse-long "Day must be an integer"]]
   ["-h" "--help"]])

(defn download-input-task [auth-file input-dir params]
  (let [{:keys [options summary errors]} (cli/parse-opts params download-input-params)]
    (cond
      (:help options)
      (println summary)

      (seq errors)
      (do (doseq [e errors]
            (println "ERROR:" e))
          (println summary))

      :else
      (download-input auth-file input-dir (:year options) (:day options)))))

(def store-auth-params
  [["-s" "--session SESSION" "Session key from browser cookie"
    :required true
    :validate [#(not (str/blank? %)) "Session key must not be blank"]]
   ["-y" "--year YEAR" "The year this session key is associated with"
    :default (str (util/get-current-year))]
   ["-h" "--help"]])

(defn store-auth-task [auth-file params]
  (let [{:keys [options summary errors]} (cli/parse-opts params store-auth-params)]
    (cond
      (:help options)
      (println summary)

      (seq errors)
      (do
        (doseq [e errors]
          (println "ERROR:" e))
        (println summary))

      :else
      (store-auth auth-file (:year options) (:session options)))))