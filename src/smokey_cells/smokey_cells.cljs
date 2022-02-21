(ns smokey-cells.smokey-cells
  (:require [smokey-cells.util :as u]
            [smokey-cells.graphics :as g]
            ["pixi.js" :as pixi]
            ["d3-delaunay" :rename {Delaunay del}]
            [iglu.core :refer [iglu->glsl]]
            [smokey-cells.smoke-3d :refer [random-square-grid-smoke-system
                                           smoke-step!
                                           smoke-system-remaining-values]]
            [clojure.walk :refer [postwalk-replace]]
            [smokey-cells.fxhash-util :refer [fxrand
                                              fxrand-int
                                              fxchoice]]))

(def features
  {:grid-size (fxchoice {:small 4
                         :medium 8
                         :large 1})
   :cell-offset (fxchoice {:small 1
                           :medium 5
                           :large 1})
   :color-offset (fxchoice {:small 2
                            :medium 3
                            :large 2})
   :cell-expansion (fxchoice {:normal 6
                              :large 1
                              :reverse 0.5})
   :edge-prominence (fxchoice {:normal 6
                               :little 3
                               :extra 2})})

(u/log "Features:" features)

(set! (.-$fxhashFeatures js/window)
      (clj->js features))

(def grid-size
  ({:small (fxrand-int 32 48)
    :medium (fxrand-int 48 72)
    :large (fxrand-int 72 128)}
   (:grid-size features)))

(def square-offset-mag
  ({:small (fxrand 0.1 0.6)
    :medium (fxrand 0.6 2.5)
    :large (fxrand-int 2.5 5)}
   (:cell-offset features)))

(def color-offset-factor
  ({:small (fxrand 0.05 0.15)
    :medium (fxrand 0.15 0.35)
    :large (fxrand-int 0.35 0.55)}
   (:color-offset features)))

(def cell-expansion-factor
  ({:normal (fxrand 1 1.45)
    :large (fxrand 1.75 3)
    :reverse (fxrand-int 0.8 0.95)}
   (:cell-expansion features)))

(def edge-factor
  ({:normal 5
    :little 2
    :extra 10}
   (:edge-prominence features)))

(def edge-offset
  ({:normal 0.025
    :little 0.05
    :extra 0.005}
   (:edge-prominence features)))

(def blur-strength (fxrand 0.2 0.9))

(def color-space-x-size (fxrand 0.2 0.5))
(def color-space-x-offset (+ (* color-space-x-size 0.5)
                             (fxrand (- 1 color-space-x-size))))
(def color-space-y-size (fxrand 0.2 0.5))
(def color-space-y-offset (+ (* color-space-y-size 0.5)
                             (fxrand (- 1 color-space-y-size))))
(def color-space-x-min (- color-space-x-size (* 0.5 color-space-x-offset)))
(def color-space-x-max (+ color-space-x-size (* 0.5 color-space-x-offset)))
(def color-space-y-min (- color-space-y-size (* 0.5 color-space-y-offset)))
(def color-space-y-max (+ color-space-y-size (* 0.5 color-space-y-offset)))

(def speed (/ 1 480))

(def convolution-dist-factor 0.0015)

(def graphics-size 4096)

(def color-fragment-source
  (str
   "varying vec2 vTextureCoord;\n"
   (iglu->glsl
    {:uniforms '{uSampler sampler2D
                 colorSpace sampler2D
                 time float}
     :signatures '{main ([] void)}
     :functions
     {'main
      (postwalk-replace
       {:color-offset-factor color-offset-factor
        :color-space-x-min color-space-x-min
        :color-space-x-max color-space-x-max
        :color-space-y-min color-space-y-min
        :color-space-y-max color-space-y-max
        :tau (* Math/PI 2)}
       '([]
         (=vec4 color (texture2D uSampler vTextureCoord))
         (=float phase (* (+ time color.z) :tau))
         (=vec2 offset (* :color-offset-factor
                          (vec2 (cos phase) (sin phase))))
         (= gl_FragColor
            (texture2D colorSpace
                       (+ (vec2 :color-space-x-min :color-space-y-min)
                          (* (+ color.xy offset)
                             (vec2 (- :color-space-x-max
                                      :color-space-x-min)
                                   (- :color-space-y-max
                                      :color-space-y-min))))))))}})))

(def edge-fragment-source
  (str
   "varying vec2 vTextureCoord;\n"
   (iglu->glsl
    {:uniforms '{uSampler sampler2D
                 size float
                 inputClamp vec4}
     :signatures '{main ([] void)
                   sigmoid ([float] float)}
     :functions
     {'sigmoid
      '([x] (/ "1.0" (+ "1.0" (exp (- x)))))
      'main
      (postwalk-replace
       {:off convolution-dist-factor
        :edge-offset (.toFixed edge-offset 2)
        :edge-factor (.toFixed edge-factor 2)
        :m0 "0.0"
        :m1 "1.0"
        :m2 "0.0"
        :m3 "1.0"
        :m4 "-4.0"
        :m5 "1.0"
        :m6 "0.0"
        :m7 "1.0"
        :m8 "0.0"}
       '([]
         (=float x vTextureCoord.x)
         (=float y vTextureCoord.y)
         (=float off (* :off
                        (- inputClamp.x inputClamp.z)))
         (=vec4 c11 (texture2D uSampler (clamp (vec2 (+ x -off)
                                                     (+ y -off))
                                               inputClamp.xy
                                               inputClamp.zw)))
         (=vec4 c12 (texture2D uSampler (clamp (vec2 x
                                                     (+ y -off))
                                               inputClamp.xy
                                               inputClamp.zw)))
         (=vec4 c13 (texture2D uSampler (clamp (vec2 (+ x off)
                                                     (+ y -off))
                                               inputClamp.xy
                                               inputClamp.zw)))
         (=vec4 c21 (texture2D uSampler (clamp (vec2 (+ x -off)
                                                     y)
                                               inputClamp.xy
                                               inputClamp.zw)))
         (=vec4 c22 (texture2D uSampler (clamp (vec2 x
                                                     y)
                                               inputClamp.xy
                                               inputClamp.zw)))
         (=vec4 c23 (texture2D uSampler (clamp (vec2 (+ x off)
                                                     y)
                                               inputClamp.xy
                                               inputClamp.zw)))
         (=vec4 c31 (texture2D uSampler (clamp (vec2 (- x off)
                                                     (+ y off))
                                               inputClamp.xy
                                               inputClamp.zw)))
         (=vec4 c32 (texture2D uSampler (clamp (vec2 x
                                                     (+ y off))
                                               inputClamp.xy
                                               inputClamp.zw)))
         (=vec4 c33 (texture2D uSampler (clamp (vec2 (+ x off)
                                                     (+ y off))
                                               inputClamp.xy
                                               inputClamp.zw)))
         (=float edge
                 (length (+ (* :m0 c11)
                            (* :m1 c12)
                            (* :m2 c13)
                            (* :m3 c21)
                            (* :m4 c22)
                            (* :m5 c23)
                            (* :m6 c31)
                            (* :m7 c32)
                            (* :m8 c33))))
         (=float edgeFactor
                 (- "1.0"
                    (max "0.0"
                         (min "1.0"
                              (* :edge-factor
                                 (- edge :edge-offset))))))
         (= gl_FragColor
            (* edgeFactor
               (texture2D uSampler
                          vTextureCoord)))))}})))

(defonce started? (atom false))
(defonce pixi-canvas (atom nil))
(defonce app (atom nil))
(defonce sprite (atom nil))
(defonce color-uniforms (atom nil))
(defonce edge-uniforms (atom nil))
(defonce smoke-system (atom nil))
(defonce current-time (atom 0))
(defonce recording? (atom false))

(defn smoke-system-steps! [steps]
  (let [initial-values (smoke-system-remaining-values @smoke-system)]
    (while (let [current-values (smoke-system-remaining-values
                                 @smoke-system)]
             (and (> current-values 0)
                  (< (- initial-values current-values)
                     steps)))
      (smoke-step! smoke-system))))

(defn rgb->hex [color]
  (let [[r g b] (mapv #(min 255 (int (* % 256)))
                      color)]
    (+ (* 256 256 r)
       (* 256 g)
       b)))

(defn project-point [origin factor point]
  (mapv #(+ %1 (* factor (- %2 %1)))
        origin
        point))

(defn expand-polygon [origin factor polygon]
  (mapv (partial project-point origin factor)
        polygon))

(defn draw-polygon [graphics points color]
  (.lineStyle graphics 0 0)
  (.beginFill graphics
              color)
  (.drawPolygon graphics
                (clj->js
                 (mapv (fn [point]
                         (let [[x y] (mapv (partial * graphics-size)
                                           point)]
                           (pixi/Point. x y)))
                       points)))
  (.endFill graphics))

(defn draw-grid! []
  (let [graphics (pixi/Graphics.)
        {:keys [assignments]} @smoke-system
        values (mapv #(or (assignments %)
                          [0 0 0])
                     (range (* grid-size grid-size)))
        colors (mapv rgb->hex
                     values)
        square-centers (mapv (fn [i]
                               (let [x (mod i grid-size)
                                     y (quot i grid-size)
                                     angle (* Math/PI 2 ((values i) 2))]
                                 (mapv #(/ (+ %1 0.5
                                              (* (%2 angle)
                                                 square-offset-mag))
                                           grid-size)
                                       [x y]
                                       [Math/cos Math/sin])))
                             (range (* grid-size grid-size)))
        triangulation (.from del (clj->js square-centers))
        bound-offset (max 0 (/ (- square-offset-mag 0.5) grid-size))
        voronoi (.voronoi triangulation (clj->js [(- bound-offset)
                                                  (- bound-offset)
                                                  (inc bound-offset)
                                                  (inc bound-offset)]))
        cells (mapv (fn [cell center]
                      (mapv (fn [point]
                              (mapv (fn [x]
                                      (u/clamp
                                       (u/scale (- bound-offset)
                                                (inc bound-offset)
                                                x)))
                                    point))
                            (expand-polygon center
                                            cell-expansion-factor
                                            cell)))
                    (js->clj (vec (.cellPolygons voronoi)))
                    square-centers)]
    (doseq [[cell color] (sort-by #(get-in % [2 0])
                                  (mapv vector cells colors values))]
      (draw-polygon graphics
                    cell
                    color))
    (reset! color-uniforms
            (clj->js {"time" 0
                      "colorSpace" (pixi/BaseTexture.from
                                    (js/document.getElementById "color-space"))}))
    (reset! edge-uniforms
            (clj->js {"size" (min (g/app-width) (g/app-height))}))
    (let [texture (.generateTexture (.-renderer @app)
                                    graphics
                                    (clj->js :scaleMode "NEAREST"))]
      (reset! sprite (pixi/Sprite. texture))
      (set! (.-filters @sprite)
            (clj->js [(pixi/filters.BlurFilter. blur-strength
                                                4
                                                pixi/settings.FILTER_RESOLUTION
                                                5)
                      (pixi/Filter. nil color-fragment-source @color-uniforms)
                      (pixi/Filter. nil edge-fragment-source @edge-uniforms)])))))

(defn init-page []
  (reset! current-time 0)
  (reset! started? true)
  (reset! smoke-system (random-square-grid-smoke-system grid-size))
  (smoke-system-steps! (* grid-size grid-size))
  (let [pixi-canvas-results (g/create-pixi-canvas! "voronoi-smoke")]
    (reset! pixi-canvas (:canvas pixi-canvas-results))
    (set! (.-style @pixi-canvas)
          "position:absolute;x:0px;y:0px;")
    (reset! app (:app pixi-canvas-results)))
  (draw-grid!)
  (.addChild (.-stage @app)
             @sprite))

(defn update-page []
  (when @started?
    (swap! current-time inc)
    (when (= @current-time 2)
      (js/fxpreview))
    (set! (.-time @color-uniforms)
          (* speed @current-time))
    (let [w (g/app-width)
          h (g/app-height)
          size (min w h)]
      (set! (.-width @pixi-canvas) w)
      (set! (.-height @pixi-canvas) h)
      (set! (.-size @edge-uniforms) size)
      (set! (.-width @sprite) size)
      (set! (.-height @sprite) size)
      (set! (.-x @sprite) (* (- w size) 0.5))
      (set! (.-y @sprite) (* (- h size) 0.5))))
  (when @recording?
    (g/save-frame @pixi-canvas @current-time)
    (when (>= (* speed (dec @current-time)) 1)
      (reset! recording? false))))

(defn record! []
  (reset! recording? true)
  (reset! current-time 0))