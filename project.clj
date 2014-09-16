(defproject shepherd-examples "0.0.1"
  :description "Examples of using Shepherd."
  :url "https://github.com/mischov/shepherd-examples"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [shepherd "0.0.1"]
                 [compojure "1.1.9"]
                 [hiccup "1.0.5"]
                 [ring "1.3.1"]
                 [criterium "0.4.3"]]

  :plugins [[lein-ring "0.8.11"]]

  :ring {:handler shepherd-examples.session/app})
