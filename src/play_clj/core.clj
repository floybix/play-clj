(ns play-clj.core
  (:require [clojure.set :as set]
            [play-clj.utils :as utils])
  (:import [com.badlogic.gdx Game Gdx Input$Keys Screen]
           [com.badlogic.gdx.graphics Camera Color GL20 OrthographicCamera
            PerspectiveCamera Texture]
           [com.badlogic.gdx.graphics.g2d Animation BitmapFont SpriteBatch
            TextureRegion]
           [com.badlogic.gdx.maps MapLayer MapLayers]
           [com.badlogic.gdx.maps.tiled TiledMap TiledMapTileLayer TmxMapLoader]
           [com.badlogic.gdx.maps.tiled.renderers
            BatchTiledMapRenderer
            HexagonalTiledMapRenderer
            IsometricStaggeredTiledMapRenderer
            IsometricTiledMapRenderer
            OrthogonalTiledMapRenderer]
           [com.badlogic.gdx.scenes.scene2d Actor Stage]
           [com.badlogic.gdx.scenes.scene2d.ui ButtonGroup CheckBox Dialog
            ImageButton ImageTextButton Label TextButton TextField]))

(defmulti create-entity class)

(defmethod create-entity TextureRegion
  [obj]
  {:type :image :object obj})

(defmethod create-entity Actor
  [obj]
  {:type :actor :object obj})

(load "core_2d")
(load "core_deprecated")
(load "core_global")
(load "core_interop")
(load "core_render")
(load "core_ui")

(defn- dummy [& args])

(defn defscreen*
  [{:keys [on-show on-render on-hide on-pause on-resize on-resume]
    :or {on-show dummy on-render dummy on-hide dummy
         on-pause dummy on-resize dummy on-resume dummy}}]
  (let [screen (atom {})
        entities (atom '())
        execute-fn! (fn [func screen-map]
                      (some->> (func screen-map @entities)
                               list
                               flatten
                               (remove nil?)
                               (reset! entities)))
        create-renderer-fn! #(swap! screen assoc :renderer (renderer %))
        update-fn! #(swap! screen merge %)]
    {:show (fn []
             (->> (swap! screen assoc
                         :total-time 0
                         :delta-time 0
                         :create-renderer-fn! create-renderer-fn!
                         :update-fn! update-fn!)
                  (execute-fn! on-show)))
     :render (fn [delta-time]
               (->> (fn [screen-map]
                      (assoc screen-map
                             :total-time (+ (:total-time screen-map) delta-time)
                             :delta-time delta-time))
                    (swap! screen)
                    (execute-fn! on-render)))
     :hide #(execute-fn! on-hide @screen)
     :pause #(execute-fn! on-pause @screen)
     :resize #(execute-fn! on-resize @screen)
     :resume #(execute-fn! on-resume @screen)}))

(defmacro defscreen
  [n & {:keys [] :as options}]
  `(->> (for [[k# v#] ~options]
          [k# (intern *ns* (symbol (str '~n "-" (name k#))) v#)])
        flatten
        (apply hash-map)
        defscreen*
        (def ~n)))

(defn defgame*
  [{:keys [on-create] :or {on-create dummy}}]
  (proxy [Game] []
    (create [] (on-create this))))

(defmacro defgame
  [n & {:keys [] :as options}]
  `(defonce ~n (defgame* ~options)))

(defn set-screen!
  [^Game game & screens]
  (let [run-fn! (fn [k & args]
                  (doseq [screen screens]
                    (apply (get screen k) args)))]
    (.setScreen game (proxy [Screen] []
                       (show [] (run-fn! :show))
                       (render [delta-time] (run-fn! :render delta-time))
                       (hide [] (run-fn! :hide))
                       (pause [] (run-fn! :pause))
                       (resize [w h] (run-fn! :resize))
                       (resume [] (run-fn! :resume))))))

(defn update!
  [{:keys [update-fn!]} & {:keys [] :as args}]
  (update-fn! args))