(ns smokey-cells.graphics
  (:require [smokey-cells.util :as u]
            ["pixi.js" :as pixi]))

(defonce page (atom nil))

(defonce page-initialized? (atom false))

(defn app-width [] (.-innerWidth js/window))
(defn app-height [] (.-innerHeight js/window))

(defn create-pixi-canvas! [name]
  (let [canvas (js/document.createElement "canvas")]
    (set! (.-id canvas) (str name))
    (set! (.-position (.-style canvas)) "absolute")
    (js/document.body.appendChild canvas)
    (let [app (pixi/Application.
               (clj->js {:view canvas
                         :width (app-width)
                         :height (app-height)
                         :resizeTo canvas}))]
      {:canvas canvas
       :app app})))

(defn save-frame [canvas & [name]]
  (let [img (.replace (.toDataURL canvas
                                  "image/png")
                      "image/png"
                      "image/octet-stream")
        a (js/document.createElement "a")]
    (doto a
      (.setAttribute "download" (str (or name "out")
                                     ".png"))
      (.setAttribute "href" img)
      (.click))))

(defn update-pages [timestamp]
  (when-not @page-initialized?
    (reset! page-initialized? true)
    ((:init @page)))
  ((:update @page))
  (js/window.requestAnimationFrame update-pages))

(defn init [page-map]
  (js/window.requestAnimationFrame update-pages)
  (reset! page page-map))
