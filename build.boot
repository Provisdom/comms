(def project 'comms)
(def version "0.1.5")

(set-env! :resource-paths #{"src"}
          :dependencies '[[adzerk/bootlaces "0.1.13" :scope "test"]
                          [org.clojure/clojure "1.8.0" :scope "provided"]
                          [org.clojure/clojurescript "1.9.229" :scope "provided"]
                          [funcool/catacumba "1.1.1" :scope "provided"]
                          [com.cognitect/transit-cljs "0.8.239"]
                          [funcool/beicon "2.3.0"]])

(require '[adzerk.bootlaces :refer [bootlaces! build-jar push-release push-snapshot]])

(bootlaces! version)

(task-options!
  pom {:project     project
       :version     version
       :description "Websocket handlers for Catacumba and Cljs"
       :url         "https://github.com/Provisdom/comms"
       :scm         {:url "https://github.com/Provisdom/comms"}
       :license     {"Eclipse Public License"
                     "http://www.eclipse.org/legal/epl-v10.html"}})

(deftask auto-build
         []
         (comp (watch) (pom) (jar) (install)))