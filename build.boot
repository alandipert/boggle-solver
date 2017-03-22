(set-env!
  :dependencies '[[adzerk/boot-cljs          "1.7.228-2"]
                  [adzerk/boot-reload        "0.4.13"]
                  [hoplon/hoplon             "6.0.0-alpha17"]
                  [org.clojure/clojure       "1.8.0"]
                  [org.clojure/clojurescript "1.9.293"]
                  [tailrecursion/boot-jetty  "0.1.3"]]
  :source-paths #{"src" "dictionaries"}
  :asset-paths  #{"assets"})

(require
  '[adzerk.boot-cljs         :refer [cljs]]
  '[adzerk.boot-reload       :refer [reload]]
  '[hoplon.boot-hoplon       :refer [hoplon prerender]]
  '[tailrecursion.boot-jetty :refer [serve]]
  '[clojure.java.io          :as io]
  '[clojure.pprint           :as pp]
  '[boggle-solver.trie       :as trie]
  '[boot.util                :as util])


(defn tokenize
  [word]
  (->> (map str word)
       (partition-all 2 1)
       (reduce
        (fn [[qu? ts] [a _ :as pair]]
          (cond qu?
                [nil ts]
                (= pair ["q" "u"])
                [true (conj ts "qu")]
                :else
                [nil (conj ts a)]))
        [nil []])
       second))

(defn gen-trie*
  [num-skip dict-file ns-sym var-sym out-file]
  (with-open [rdr (io/reader dict-file)
              out (io/writer out-file)]
    (let [words (drop num-skip (doall (line-seq rdr)))
          trie  (trie/build-trie (map tokenize words))]
      (binding [*out* out]
        (print (format "(ns %s)\n" ns-sym))
        (print (format "(def %s\n" var-sym))
        (pr trie)
        (print "\n)")))))

(deftask gen-cljs-dict-task
  []
  (with-pre-wrap [fs]
    (if-let [input (first (by-name ["twl06.txt"] (input-files fs)))]
      (do (util/info "Generating boggle_solver/dict.cljs for %s...\n" "twl06.txt")
          (let [tmpd (tmp-dir!)
                outf (doto (io/file tmpd "boggle_solver" "dict.cljs")
                       io/make-parents)]
            (gen-trie* 2 (tmp-file input) 'boggle-solver.dict 'twl06-trie outf)
            (->> (add-source fs tmpd)
                 commit!)))
      (do (util/info "Couldn't find dictionary, skipping\n")
          fs))))

(deftask dev
  "Build boggle-solver for local development."
  []
  (comp
   (gen-cljs-dict-task)
   (watch)
   (speak)
   (hoplon)
   (reload)
   (cljs)
   (serve :port 8000)))

(deftask prod
  "Build boggle-solver for production deployment."
  []
  (comp
   (gen-cljs-dict-task)
   (hoplon)
   (cljs :optimizations :advanced)
   (target :dir #{"target"})))
