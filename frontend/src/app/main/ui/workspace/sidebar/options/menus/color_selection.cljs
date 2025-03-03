;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC

(ns app.main.ui.workspace.sidebar.options.menus.color-selection
  (:require-macros [app.main.style :as stl])
  (:require
   [app.common.data :as d]
   [app.common.data.macros :as dm]
   [app.common.types.color :as ctc]
   [app.common.uuid :as uuid]
   [app.main.data.workspace.colors :as dc]
   [app.main.data.workspace.selection :as dws]
   [app.main.store :as st]
   [app.main.ui.components.title-bar :refer [title-bar]]
   [app.main.ui.hooks :as h]
   [app.main.ui.workspace.sidebar.options.rows.color-row :refer [color-row]]
   [app.util.i18n :as i18n :refer [tr]]
   [rumext.v2 :as mf]))

(defn- prepare-colors
  [shapes file-id shared-libs]
  (let [data           (into [] (remove nil? (ctc/extract-all-colors shapes file-id shared-libs)))
        groups         (d/group-by :attrs #(dissoc % :attrs) data)
        all-colors     (distinct (mapv :attrs data))

        tmp            (group-by #(some? (:id %)) all-colors)
        library-colors (get tmp true)
        colors         (get tmp false)]
    {:groups groups
     :all-colors all-colors
     :colors colors
     :library-colors library-colors}))

(def xf:map-shape-id
  (map :shape-id))

(mf/defc color-selection-menu
  {::mf/wrap [#(mf/memo' % (mf/check-props ["shapes"]))]
   ::mf/wrap-props false}
  [{:keys [shapes file-id shared-libs]}]
  (let [{:keys [groups library-colors colors]}
        (mf/with-memo [shapes file-id shared-libs]
          (prepare-colors shapes file-id shared-libs))

        state*           (mf/use-state true)
        open?            (deref state*)

        has-colors?      (or (some? (seq colors)) (some? (seq library-colors)))

        toggle-content   (mf/use-fn #(swap! state* not))

        expand-lib-color (mf/use-state false)
        expand-color     (mf/use-state false)

        groups-ref       (h/use-ref-value groups)
        prev-colors-ref  (mf/use-ref nil)

        initial-color-keys
        (mf/use-memo
         #(->> (concat colors library-colors)
               (reduce
                (fn [result color]
                  (assoc result color (dm/str (uuid/next))))
                {})))

        color-keys*  (mf/use-var initial-color-keys)

        on-change
        (mf/use-fn
         (fn [new-color old-color from-picker?]
           (let [old-color   (-> old-color
                                 (dissoc :name :path)
                                 (d/without-nils))

                 ;; When dragging on the color picker sometimes all
                 ;; the shapes hasn't updated the color to the prev
                 ;; value so we need this extra calculation
                 groups      (mf/ref-val groups-ref)
                 prev-colors (mf/ref-val prev-colors-ref)

                 prev-color  (d/seek (partial get groups) prev-colors)

                 cops-old    (get groups old-color)
                 cops-prev   (get groups prev-colors)
                 cops        (or cops-prev cops-old)
                 old-color   (or prev-color old-color)]

             (when from-picker?
               (let [color (-> new-color
                               (dissoc :name :path)
                               (d/without-nils))]
                 (mf/set-ref-val! prev-colors-ref
                                  (conj prev-colors color))))

             (swap! color-keys* assoc new-color (get @color-keys* old-color))
             (st/emit! (dc/change-color-in-selected cops new-color old-color)))))

        on-open
        (mf/use-fn #(mf/set-ref-val! prev-colors-ref []))

        on-close
        (mf/use-fn #(mf/set-ref-val! prev-colors-ref []))

        on-detach
        (mf/use-fn
         (fn [color]
           (let [groups (mf/ref-val groups-ref)
                 cops   (get groups color)
                 color' (dissoc color :id :file-id)]
             (st/emit! (dc/change-color-in-selected cops color' color)))))

        select-only
        (mf/use-fn
         (fn [color]
           (let [groups (mf/ref-val groups-ref)
                 cops   (get groups color)
                 ids    (into (d/ordered-set) xf:map-shape-id cops)]
             (st/emit! (dws/select-shapes ids)))))]

    [:div {:class (stl/css :element-set)}
     [:div {:class (stl/css :element-title)}
      [:& title-bar {:collapsable  has-colors?
                     :collapsed    (not open?)
                     :on-collapsed toggle-content
                     :title        (tr "workspace.options.selection-color")
                     :class        (stl/css-case :title-spacing-selected-colors (not has-colors?))}]]

     (when open?
       [:div {:class (stl/css :element-content)}
        [:div {:class (stl/css :selected-color-group)}
         ;; The hidden color is to solve a problem with the color picker. When a color is changed
         ;; and is no longer a library color it disapears from the list of library colors. Because
         ;; we need to keep the color picker open we need to maintain that color. The easier way
         ;; is to render the color elements so even if the library color is no longer we have still
         ;; the component to change it from the color picker.
         (let [lib-colors (cond->> library-colors (not @expand-lib-color) (take 3))
               lib-colors (concat lib-colors colors)]
           (for [[index color] (d/enumerate lib-colors)]
             [:& color-row
              {:key (get @color-keys* color)
               :color color
               :index index
               :hidden (not (:id color))
               :on-detach on-detach
               :select-only select-only
               :on-change #(on-change %1 color %2)
               :on-open on-open
               :on-close on-close}]))
         (when (and (false? @expand-lib-color) (< 3 (count library-colors)))
           [:button  {:class (stl/css :more-colors-btn)
                      :on-click #(reset! expand-lib-color true)}
            (tr "workspace.options.more-lib-colors")])]

        [:div {:class (stl/css :selected-color-group)}
         (for [[index color] (d/enumerate (cond->> colors (not @expand-color) (take 3)))]
           [:& color-row
            {:key (get @color-keys* color)
             :color color
             :index index
             :select-only select-only
             :on-change #(on-change %1 color %2)
             :on-open on-open
             :on-close on-close}])

         (when (and (false? @expand-color) (< 3 (count colors)))
           [:button  {:class (stl/css :more-colors-btn)
                      :on-click #(reset! expand-color true)}
            (tr "workspace.options.more-colors")])]])]))
