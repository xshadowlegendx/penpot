;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC

(ns app.main.ui.workspace.sidebar.assets
  (:require-macros [app.main.style :as stl])
  (:require
   [app.common.data.macros :as dm]
   [app.config :as cf]
   [app.main.data.modal :as modal]
   [app.main.data.workspace :as dw]
   [app.main.data.workspace.assets :as dwa]
   [app.main.refs :as refs]
   [app.main.store :as st]
   [app.main.ui.components.context-menu-a11y :refer [context-menu*]]
   [app.main.ui.components.search-bar :refer [search-bar]]
   [app.main.ui.context :as ctx]
   [app.main.ui.ds.buttons.icon-button :refer [icon-button*]]
   [app.main.ui.icons :as i]
   [app.main.ui.workspace.sidebar.assets.common :as cmm]
   [app.main.ui.workspace.sidebar.assets.file-library :refer [file-library]]
   [app.util.dom :as dom]
   [app.util.i18n :as i18n :refer [tr]]
   [cuerdas.core :as str]
   [rumext.v2 :as mf]))

(mf/defc assets-libraries
  {::mf/wrap [mf/memo]
   ::mf/wrap-props false}
  [{:keys [filters]}]
  (let [libraries (mf/deref refs/workspace-libraries)
        libraries (mf/with-memo [libraries]
                    (->> (vals libraries)
                         (remove :is-indirect)
                         (map (fn [file]
                                (update file :data dissoc :pages-index)))
                         (sort-by #(str/lower (:name %)))))]
    (for [file libraries]
      [:& file-library
       {:key (dm/str (:id file))
        :file file
        :local? false
        :default-open? false
        :filters filters}])))

(mf/defc assets-local-library
  {::mf/wrap [mf/memo]
   ::mf/wrap-props false}
  [{:keys [filters]}]
  ;; NOTE: as workspace-file is an incomplete view of file (it do not
  ;; contain :data), we need to reconstruct it using workspace-data
  (let [file   (mf/deref refs/workspace-file)
        data   (mf/deref refs/workspace-data)
        data   (mf/with-memo [data]
                 (dissoc data :pages-index))
        file   (mf/with-memo [file data]
                 (assoc file :data data))]

    [:& file-library
     {:file file
      :local? true
      :default-open? true
      :filters filters}]))

(defn- toggle-values
  [v [a b]]
  (if (= v a) b a))

(mf/defc assets-toolbox
  {::mf/wrap [mf/memo]
   ::mf/wrap-props false}
  [{:keys [size]}]
  (let [components-v2  (mf/use-ctx ctx/components-v2)
        read-only?     (mf/use-ctx ctx/workspace-read-only?)
        filters*       (mf/use-state
                        {:term ""
                         :section "all"
                         :ordering (dwa/get-current-assets-ordering)
                         :list-style (dwa/get-current-assets-list-style)
                         :open-menu false})
        filters        (deref filters*)
        term           (:term filters)
        list-style     (:list-style filters)
        menu-open?     (:open-menu filters)
        section        (:section filters)
        ordering       (:ordering filters)
        reverse-sort?  (= :desc ordering)
        num-libs       (count (mf/deref refs/workspace-libraries))

        show-templates-04-test1?
        (and (cf/external-feature-flag "templates-04" "test1") (zero? num-libs))

        show-templates-04-test2?
        (and (cf/external-feature-flag "templates-04" "test2") (zero? num-libs))

        toggle-ordering
        (mf/use-fn
         (mf/deps ordering)
         (fn []
           (let [new-value (toggle-values ordering [:asc :desc])]
             (swap! filters* assoc :ordering new-value)
             (dwa/set-current-assets-ordering! new-value))))

        toggle-list-style
        (mf/use-fn
         (mf/deps list-style)
         (fn []
           (let [new-value (toggle-values list-style [:thumbs :list])]
             (swap! filters* assoc :list-style new-value)
             (dwa/set-current-assets-list-style! new-value))))

        on-search-term-change
        (mf/use-fn
         (fn [event]
           (st/emit! (dw/clear-assets-section-open))
           (swap! filters* assoc :term event)))

        on-section-filter-change
        (mf/use-fn
         (fn [event]
           (let [value (or (-> (dom/get-target event)
                               (dom/get-value))
                           (as-> (dom/get-current-target event) $
                             (dom/get-attribute $ "data-testid")))]
             (st/emit! (dw/clear-assets-section-open))
             (swap! filters* assoc :section value :open-menu false))))

        show-libraries-dialog
        (mf/use-fn
         (fn []
           (modal/show! :libraries-dialog {})
           (modal/allow-click-outside!)))

        on-open-menu
        (mf/use-fn  #(swap! filters* update :open-menu not))

        on-menu-close
        (mf/use-fn #(swap! filters* assoc :open-menu false))

        options
        [{:name    (tr "workspace.assets.box-filter-all")
          :id      "all"
          :handler on-section-filter-change}
         {:name    (tr "workspace.assets.components")
          :id      "components"
          :handler on-section-filter-change}

         (when (not components-v2)
           {:name    (tr "workspace.assets.graphics")
            :id      "graphics"
            :handler on-section-filter-change})

         {:name    (tr "workspace.assets.colors")
          :id      "colors"
          :handler on-section-filter-change}

         {:name    (tr "workspace.assets.typography")
          :id      "typographies"
          :handler on-section-filter-change}]]

    [:article  {:class (stl/css :assets-bar)}
     [:div {:class (stl/css :assets-header)}
      (when-not ^boolean read-only?
        (cond
          show-templates-04-test1?
          [:button {:class (stl/css :libraries-button)
                    :on-click show-libraries-dialog
                    :data-testid "libraries"}
           (tr "workspace.assets.add-library")]
          show-templates-04-test2?
          [:button {:class (stl/css :add-library-button)
                    :on-click show-libraries-dialog
                    :data-testid "libraries"}
           (tr "workspace.assets.add-library")]
          :else
          [:button {:class (stl/css :libraries-button)
                    :on-click show-libraries-dialog
                    :data-testid "libraries"}
           [:span {:class (stl/css :libraries-icon)}
            i/library]
           (tr "workspace.assets.libraries")]))


      [:div {:class (stl/css :search-wrapper)}
       [:& search-bar {:on-change on-search-term-change
                       :value term
                       :placeholder (tr "workspace.assets.search")}
        [:button
         {:on-click on-open-menu
          :title (tr "workspace.assets.filter")
          :class (stl/css-case :section-button true
                               :opened menu-open?)}
         i/filter-icon]]
       [:> context-menu*
        {:on-close on-menu-close
         :selectable true
         :selected section
         :show menu-open?
         :fixed true
         :min-width true
         :width size
         :top 158
         :left 18
         :options options}]
       [:> icon-button* {:variant "ghost"
                         :aria-label (tr "workspace.assets.sort")
                         :on-click toggle-ordering
                         :icon (if reverse-sort? "asc-sort" "desc-sort")}]]]

     [:& (mf/provider cmm/assets-filters) {:value filters}
      [:& (mf/provider cmm/assets-toggle-ordering) {:value toggle-ordering}
       [:& (mf/provider cmm/assets-toggle-list-style) {:value toggle-list-style}
        [:*
         [:& assets-local-library {:filters filters}]
         [:& assets-libraries {:filters filters}]]]]]]))
