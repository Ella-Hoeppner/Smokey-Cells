(ns smokey-cells.core
  (:require [smokey-cells.graphics :as graphics]
            [smokey-cells.smokey-cells :refer [init-page
                                         update-page]]))

(defn init []
  (js/console.log "Initializing...")
  (graphics/init
   {:init init-page
    :update update-page}))
