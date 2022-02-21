(ns smokey-cells.smoke-3d
  (:require [smokey-cells.util :as u :refer [update!]]
            [smokey-cells.fxhash-util :refer [fxrand
                                        fxrand-nth
                                        fxshuffle]]
            ["d3-octree" :refer [octree]]))

(defn average [& vecs]
  (let [vec-count (count vecs)]
    (apply (partial mapv (comp #(/ % vec-count) +))
          vecs)))

(defn remove-nth [coll n]
  (concat (take n coll)
          (drop (inc n) coll)))

(defn symmetrize-graph [graph]
  (let [size (count graph)]
    (mapv (comp vec persistent!)
          (persistent!
           (reduce (fn [new-graph i]
                     (reduce (fn [new-graph adjacency]
                               (-> new-graph
                                   (update! i #(conj! % adjacency))
                                   (update! adjacency #(conj! % i))))
                             new-graph
                             (graph i)))
                   (transient
                    (vec (repeatedly size #(transient #{}))))
                   (range size))))))

(defn square-grid-graph [side]
  (symmetrize-graph
     (mapv (fn [index]
             (disj #{(when (not (zero? (mod (inc index)
                                            side)))
                       (inc index))
                     (when (< index
                              (* side (dec side)))
                       (+ index side))}
                   nil))
           (range (* side side)))))

(defn smoke-system [graph values initial-assignments]
  (let [tree (octree (clj->js values))
        size (count graph)
        initial-assignment-keys (keys initial-assignments)]
    {:graph graph
     :tree tree
     :size size
     :values values
     :assignments (reduce (fn [assignments [graph-index value]]
                            (assoc! assignments graph-index value))
                          (transient (vec (repeat size nil)))
                          initial-assignments)
     :graph-edge-indeces initial-assignment-keys
     :graph-unused-indeces (transient (set
                                       (remove (set initial-assignment-keys)
                                               (range size))))
     :graph-used-indeces (transient (set initial-assignment-keys))}))

(defn smoke-step! [system-atom]
  (let [{:keys [graph
                tree
                assignments
                graph-edge-indeces
                graph-unused-indeces
                graph-used-indeces]}
        @system-atom]
    (when (not (or (zero? (.size tree))
                   (zero? (count graph-unused-indeces))
                   (empty? graph-edge-indeces)))
      (let [graph-edge-metaindex (fxrand-nth (range (count graph-edge-indeces)))
            graph-edge-index (nth graph-edge-indeces graph-edge-metaindex)
            unused-adjacencies (remove graph-used-indeces
                                       (graph graph-edge-index))]
        (if (empty? unused-adjacencies)
          (do (swap! system-atom
                     assoc
                     :graph-edge-indeces
                     (remove-nth graph-edge-indeces graph-edge-metaindex))
              nil)
          (let [chosen-adjacency (fxrand-nth unused-adjacencies)
                [x y z] (apply average
                               (filter identity
                                       (map assignments
                                            (graph chosen-adjacency))))
                best-value-array (.find tree x y z)
                best-value (js->clj best-value-array)]
            (.remove tree best-value-array)
            (swap! system-atom
                   assoc
                   :assignments
                   (assoc! assignments
                           chosen-adjacency
                           best-value)
                   :graph-edge-indeces
                   (conj graph-edge-indeces chosen-adjacency)
                   :graph-unused-indeces
                   (disj! graph-unused-indeces chosen-adjacency)
                   :graph-used-indeces
                   (conj! graph-used-indeces chosen-adjacency))
            [chosen-adjacency best-value]))))))

(defn smoke-system-remaining-values [system]
  (.size (:tree system)))

(defn random-square-grid-smoke-system [side & initial-assignment-count]
  (let [initial-assignment-count (or initial-assignment-count 1)
        graph (square-grid-graph side)
        generate-value #(vec (repeatedly 3 fxrand))
        values (vec (repeatedly (- (* side side) initial-assignment-count)
                                generate-value))
        initial-assignment-indeces (take initial-assignment-count
                                         (fxshuffle (range (* side side))))
        initial-assignments (reduce #(assoc %1 %2 (generate-value))
                                    {}
                                    initial-assignment-indeces)]
    (smoke-system graph
                     values
                     initial-assignments)))

(defn smoke-assignments [system-atom]
  (let [{:keys [assignments]} @system-atom]
    (mapv #(or (assignments %)
               [0 0 0])
          (range (count assignments))))
  #_(let [{:keys [assignments]} @system-atom
        p-assignments (persistent! assignments)]
    (swap! system-atom
           assoc
           :assignments (transient p-assignments))
    (mapv #(if %
             %
             [0 0 0])
          p-assignments)))
