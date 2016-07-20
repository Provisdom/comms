(def project 'comms)
(def version "0.1.0-SNAPSHOT")

(set-env! :resource-paths #{"src"}
          :dependencies   '[[org.clojure/clojure "1.8.0" :scope "provided"]
                            [org.clojure/clojurescript "1.9.93" :scope "provided"]
                            [com.cognitect/transit-cljs "0.8.239"]
                            [funcool/beicon "2.1.0"]
                            [funcool/catacumba "1.0.0-SNAPSHOT"]])

(task-options!
  pom {:project     project
       :version     version
       :description "FIXME: write description"
       :url         "http://example/FIXME"
       :scm         {:url "https://github.com/yourname/postal"}
       :license     {"Eclipse Public License"
                     "http://www.eclipse.org/legal/epl-v10.html"}})

(deftask auto-build
         []
         (comp (watch) (pom) (jar) (install)))