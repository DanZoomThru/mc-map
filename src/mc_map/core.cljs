(ns mc-map.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :refer [put! chan <!]]))

(enable-console-print!)

(defn log [& more]
  (.log js/console (apply str more)))

(def SIXTEENTH_AND_MISSION (google.maps.LatLng. 37.764847 -122.420042))
(def ALPHABET_CITY (google.maps.LatLng. 40.724545 -73.979050))

(def app-state (atom {:mapOptions #js {:center SIXTEENTH_AND_MISSION
                                       :zoom 14}
                      :points []}))

(defn mc-click-handler
  "This function is invoked when a feature in the mc-layer is clicked"
  [e]
  (log (js->clj (.. e -latLng)))
  ;(calc-route HOME (.. e -latLng))
  )

(defn add-mc-layer
  "return the mc-layer"
  [the-map click-handler]
  (let [mc-layer (google.maps.KmlLayer. #js {:url "http://nodename.com/MotorcycleMeters_20130412.kml"
                                             :preserveViewport true ; don't zoom or position the map to the
                                                                    ; KmlLayer's bounds when showing the layer
                                             :suppressInfoWindows true ; don't let clickable features in the layer
                                                                       ; trigger display of InfoWindow objects
                                             })]
    (.setMap mc-layer the-map)
    (google.maps.event/addListener mc-layer "click" click-handler)
    mc-layer))

(defn toggle-mc-layer
  [owner]
  (let [mc-layer (om/get-state owner :mc-layer)
        google-map (om/get-state owner :google-map)
        current-map (.-map mc-layer)]
    (.setMap mc-layer (if current-map nil google-map))))

(defn marker-from-point [point google-map]
  (google.Maps.Marker. #js {:position (google.Maps.LatLng (.latitude point) (.longitude point))
                            :map google-map
                            :title (.label point)}))

(defn update-markers [points {:keys [google-map markers] :as owner}]
  (map markers #(.setMap % nil))
  (let [new-markers (map #(marker-from-point % google-map) points)]
    (om/set-state! owner :markers new-markers)))

(defn map-view [app owner]
  (reify
    om/IInitState
    (init-state [_]
                {:move (chan)  ; channel for move requests
                 :toggle (chan) ; channel for toggle requests
                 :google-map nil ; the Google Maps object
                 :mc-layer nil ; the motorcycle meters map layer
                 :markers []})

    om/IWillMount
    (will-mount
     [_]
     ;; establish go-loops that will listen for requests and act on them:
     (let [toggle (om/get-state owner :toggle)]
       (go (loop []
             (<! toggle)
             (toggle-mc-layer owner)
             (recur))))
     (let [move (om/get-state owner :move)]
       (go (loop []
             (let [loc (<! move)]
               (om/update! app assoc-in [:mapOptions] {:center loc :zoom 14}))
             (recur)))))

    om/IRenderState
    (render-state [this {:keys [move toggle google-map]}]
                  (when (and google-map (:center (:mapOptions app)))
                    (.panTo google-map (:center (:mapOptions app))))

                  (dom/div #js {:style #js {:width "100%" :height "100%"}}
                           (dom/div #js {:id "map-holder" :style #js {:width "80%" :height "100%" :float "left"}})
                           (dom/button #js {:width "20%" :float "left"
                                            :onClick (fn [e]
                                                       (put! toggle :request))}
                                       "Toggle MC Meters")
                           (dom/button #js {:width "20%" :float "left"
                                            :onClick (fn [e] (put! move ALPHABET_CITY))}
                                       "Move to New York")))

    om/IDidMount
    (did-mount [this node]
               (let [the-map (google.maps.Map. (. js/document (getElementById "map-holder"))
                                               (:mapOptions app))]
                 (om/set-state! owner :google-map the-map)
                 (let [mc-layer (add-mc-layer the-map mc-click-handler)]
                   (om/set-state! owner :mc-layer mc-layer)))
               (update-markers (:points app) owner))))

(om/root
  app-state
  map-view
  (. js/document (getElementById "content")))
