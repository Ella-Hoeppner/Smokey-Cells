(ns smokey-cells.fxhash-util)

(defn fxrand 
  ([] (js/fxrand))
  ([maximum] (* maximum (fxrand)))
  ([minimum maximum] (+ minimum (fxrand (- maximum minimum)))))

(defn fxchance [chance]
  (> chance (fxrand)))

(defn fxchoice [option-weight-map]
  (let [weight-sum (apply + (vals option-weight-map))]
    (loop [choice-index (fxrand)
           option-weight-pairs (seq option-weight-map)]
      (if (= 1 (count option-weight-pairs))
        (first (first option-weight-pairs))
        (let [[option weight] (first option-weight-pairs)
              new-index (- choice-index (/ weight weight-sum))]
          (if (<= new-index 0)
            option
            (recur new-index
                   (rest option-weight-pairs))))))))

(defn fxrand-int
  ([maximum] (Math/floor (fxrand maximum)))
  ([minimum maximum] (+ minimum (fxrand-int (- maximum minimum)))))

(defn fxrand-nth [coll]
  (nth coll
       (Math/floor (* (fxrand) (count coll)))))

(defn fxshuffle [coll]
  (loop [sorted-coll coll
         shuffled-list ()]
    (let [size (count sorted-coll)]
      (if (pos? size)
        (let [index (Math/floor (fxrand size))]
          (recur (concat (take index sorted-coll)
                         (drop (inc index) sorted-coll))
                 (conj shuffled-list (nth sorted-coll index))))
        shuffled-list))))

