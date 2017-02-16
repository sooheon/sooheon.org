(set-env!
 :source-paths #{"src" "content"}
 :resource-paths #{"resources"}
 :dependencies '[[org.clojure/tools.nrepl "0.2.12" :exclusions [org.clojure/clojure]]
                 [pandeiro/boot-http "0.7.6" :exclusions [org.clojure/clojure]]
                 [deraen/boot-livereload "0.2.0"]
                 [perun "0.4.1-SNAPSHOT"]
                 [hiccup "1.0.5" :exclusions [org.clojure/clojure]]
                 [clj-time "0.13.0"]])

(require '[pandeiro.boot-http :refer [serve]]
         '[boot.util :as util]
         '[deraen.boot-livereload :refer [livereload]]
         '[io.perun :as p]
         '[io.perun.core :as perun]
         '[site.layout :as layout]
         '[clojure.string :as string])

(task-options!
 pom {:project 'sooheon.com
      :version "0.1.0"})

(deftask draft
  "Creates a new post with frontmatter and uuid"
  [n filename FILENAME str "Filename of new post"]
  (let [uuid (java.util.UUID/randomUUID)
        filepath (format "content/drafts/%s" filename)
        front-matter (format "---\ntitle: %s \nuuid: %s\ndate-published: \n---\n"
                             (-> (string/split filename #"\.")
                                 first
                                 (string/replace #"-" " "))
                             uuid)]
    (spit filepath front-matter)))

(defn published-post? [{:keys [path]}]
  (.startsWith path "posts/"))

(defn slug-fn [filename]
  "Parses `slug` portion out of the filename in the format: slug-title.ext"
  (->> (string/split filename #"[-\.]")
       drop-last
       (string/join "-")
       string/lower-case))

(defn permalink-fn [{:keys [date-published slug]}]
  (let [date (string/split (layout/iso-date-fmt date-published) #"-")
        year (first date)
        month (second date)]
    #_(str "/" year "/" month "/" slug ".html")
    (str "/" slug ".html")))

(deftask build
  "Build sooheon.com"
  [i include-drafts bool "Include drafts?"]
  (comp
   (p/global-metadata)
   (p/markdown :options {:extensions {:smartypants true}})
   (p/slug :slug-fn slug-fn)
   (p/permalink :permalink-fn permalink-fn)
   (p/canonical-url)
   (p/collection :renderer 'site.layout/index-page :filterer published-post?)
   (p/render :renderer 'site.layout/post-page :filterer published-post?)
   (p/atom-feed :filterer published-post?)
   (p/rss :filterer published-post?)
   (p/sitemap :filterer #(not= (:slug %) "404"))))

(deftask dev
  "Build sooheon.com for local development"
  []
  (comp
   (serve :resource-root "public" :port 4000)
   (watch)
   (build)
   #_(p/print-meta)
   (livereload :asset-path "public")))

(deftask deploy
  "Build with Google Analytics script injected and output to target"
  []
  (comp (build)
        (p/inject-scripts :scripts #{"ga-inject.js"})
        (target)))