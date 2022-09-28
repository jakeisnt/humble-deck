(ns humble-deck.controls
  (:require
    [humble-deck.resources :as resources]
    [humble-deck.slides :as slides]
    [humble-deck.state :as state]
    [io.github.humbleui.canvas :as canvas]
    [io.github.humbleui.core :as core]
    [io.github.humbleui.paint :as paint]
    [io.github.humbleui.protocols :as protocols]
    [io.github.humbleui.ui :as ui]
    [io.github.humbleui.window :as window])
  (:import
    [io.github.humbleui.skija FilterTileMode ImageFilter]))

(defn safe-add [x y from to]
  (-> (+ x y)
    (min (dec to))
    (max from)))

(defn toggle-modes []
  (case (:mode @state/*state)
    :present  (swap! state/*state assoc
                :mode :overview
                :animation-end (core/now))
    :overview (swap! state/*state assoc
                :animation-start (core/now))))

(defn key-listener [child]
  (ui/key-listener
    {:on-key-down
     (fn [{:keys [key modifiers]}]
       (let [cmd?   (modifiers :mac-command)
             window @state/*window]
         (cond
           (and cmd? (= :left key))
           (swap! state/*state update :current safe-add -1000000 0 (count slides/slides))

           (and cmd? (= :right key))
           (swap! state/*state update :current safe-add 1000000 0 (count slides/slides))

           (#{:up :left :page-up} key)
           (swap! state/*state update :current safe-add -1 0 (count slides/slides))

           (#{:down :right :page-down :space} key)
           (swap! state/*state update :current safe-add 1 0 (count slides/slides))
           
           (= :t key)
           (toggle-modes)
           
           (= :f key)
           (let [full-screen? (window/full-screen? window)]
             (window/set-full-screen window (not full-screen?)))
           
           (and 
             (= :escape key)
             (window/full-screen? window))
           (window/set-full-screen window false))))}
    child))

(defn hide-controls! []
  (swap! state/*state assoc :controls? false)
  (when-some [cancel-timer (:controls-timer @state/*state)]
    (cancel-timer))
  (swap! state/*state assoc :controls-timer nil)
  (window/hide-mouse-cursor-until-moved @state/*window))

(defn show-controls! []
  (when-some [cancel-timer (:controls-timer @state/*state)]
    (cancel-timer))
  (swap! state/*state assoc
    :controls-timer (core/schedule hide-controls! 5000)
    :controls?      true))

(defmacro template-icon-button [icon & on-click]
  `(ui/width 40
     (ui/height 40
       (ui/button (fn [] ~@on-click)
         (ui/width 14
           (ui/height 14
             ~icon))))))

(def thumb-w
  3)

(def thumb-h
  18)

(def track-h
  3)

(core/deftype+ SliderThumb []
  protocols/IComponent
  (-measure [_ ctx cs]
    (let [{:keys [scale]} ctx]
      (core/size (* scale thumb-w) (* scale thumb-h))))

  (-draw [this ctx rect ^Canvas canvas]
    (let [{:keys [scale]} ctx
          rrect (core/rrect-xywh (:x rect) (:y rect) (:width rect) (:height rect) (* scale 4))]
      (with-open [fill   (paint/fill 0xCCFFFFFF)
                  stroke (paint/stroke 0xCCFFFFFF (* scale 2))]
        (canvas/draw-rect canvas rrect fill)
        #_(canvas/draw-rect canvas rrect stroke))))
  
  (-event [this ctx event])

  (-iterate [this ctx cb]))

(core/deftype+ SliderTrackActive []
  protocols/IComponent
  (-measure [_ ctx cs]
    cs)

  (-draw [this ctx ^IRect rect ^Canvas canvas]
    (let [{:keys [scale]} ctx
          track-h      (* scale track-h)
          half-track-h (/ track-h 2)
          half-thumb-w (-> thumb-w (* scale) (/ 2))
          x      (- (:x rect) half-track-h)
          y      (+ (:y rect) (/ (:height rect) 2) (- half-track-h))
          w      (+ (:width rect) half-track-h (- half-thumb-w) (- (* 2 scale)))
          r      half-track-h
          rect   (core/rrect-xywh x y w track-h r 0 0 r)]
      (when (pos? w)
        (with-open [fill (paint/fill 0xCCFFFFFF)]
          (canvas/draw-rect canvas rect fill)))))
  
  (-event [this ctx event])

  (-iterate [this ctx cb]))

(core/deftype+ SliderTrackInactive []
  protocols/IComponent
  (-measure [_ ctx cs]
    cs)

  (-draw [this ctx ^IRect rect ^Canvas canvas]
    (let [{:keys [scale]}   ctx
          track-h      (* scale track-h)
          half-track-h (/ track-h 2)
          half-thumb-w (-> thumb-w (* scale) (/ 2))
          x      (+ (:x rect) half-thumb-w (* 2 scale))
          y      (+ (:y rect) (/ (:height rect) 2) (- half-track-h))
          w      (+ (:width rect) half-track-h (- half-thumb-w) (- (* 2 scale)))
          r      half-track-h
          rect   (core/rrect-xywh x y w track-h 0 r r 0)]
      (when (pos? w)
        (with-open [fill (paint/fill 0x60FFFFFF)]
          (canvas/draw-rect canvas rect fill)))))
  
  (-event [this ctx event])

  (-iterate [this ctx cb]))


(def controls
  (ui/mouse-listener
    {:on-move (fn [_] (show-controls!))
     :on-over (fn [_] (show-controls!))
     :on-out  (fn [_] (hide-controls!))}
    (ui/valign 1
      (ui/dynamic _ [controls? (:controls? @state/*state)]
        (if (not controls?)
          (ui/gap 0 0)
          (ui/with-context
            {:fill-text                 (paint/fill 0xCCFFFFFF)
             :hui.button/bg-active      (paint/fill 0x80000000)
             :hui.button/bg-hovered     (paint/fill 0x40000000)
             :hui.button/bg             (paint/fill 0x00000000)
             :hui.button/padding-left   0
             :hui.button/padding-top    0
             :hui.button/padding-right  0
             :hui.button/padding-bottom 0
             :hui.button/border-radius  0}
            (ui/backdrop (ImageFilter/makeBlur 70 70 FilterTileMode/CLAMP)
              (ui/rect (paint/fill 0x50000000)
                (ui/row
                  (template-icon-button resources/icon-prev
                    (swap! state/*state update :current safe-add -1 0 (count slides/slides))
                    (show-controls!))

                  (template-icon-button resources/icon-next
                    (swap! state/*state update :current safe-add 1 0 (count slides/slides))
                    (show-controls!))

                  (ui/gap 14 0)
                                    
                  [:stretch 1
                   (ui/dynamic _ [mode (:mode @state/*state)]
                     (if (= :present mode)
                       (ui/valign 0.5
                         (ui/slider
                           {:track-active   (->SliderTrackActive)
                            :track-inactive (->SliderTrackInactive)
                            :thumb          (->SliderThumb)}
                           state/*slider))
                       (ui/gap 0 0)))]
                  
                  (ui/gap 14 0)
                  
                  (ui/dynamic _ [mode (:mode @state/*state)]
                    (template-icon-button
                      (case mode
                        :overview resources/icon-present
                        :present  resources/icon-overview)
                      (toggle-modes)))
                  
                  (ui/dynamic ctx [window       (:window ctx)
                                   full-screen? (window/full-screen? window)]
                    (template-icon-button 
                      (if full-screen?
                        resources/icon-windowed
                        resources/icon-full-screen)
                      (window/set-full-screen window (not full-screen?)))))))))))))
