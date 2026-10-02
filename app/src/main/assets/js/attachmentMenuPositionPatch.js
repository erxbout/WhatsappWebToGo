/**
 * Installs a resilient WhatsApp attachment-menu position correction.
 *
 * On narrow screens, ".app-wrapper-web" performs horizontal scrolling.
 * WhatsApp does not include that element's scrollLeft when positioning popup
 * menus, so the attachment menu appears increasingly far left as the user
 * scrolls toward the chat pane.
 *
 * The outer function is immediately invoked when Java evaluates this asset.
 */
(function installAttachmentMenuPositionPatch() {
    "use strict";

    /**
     * Applies horizontal and vertical scroll compensation to all currently
     * displayed WhatsApp popup-menu containers.
     *
     * WhatsApp positions popup menus without fully accounting for scrolling
     * performed by ".app-wrapper-web":
     *
     * - scrollLeft causes the popup to appear too far left.
     * - scrollTop causes the popup to appear too far upward.
     *
     * The independent CSS translate property preserves WhatsApp's existing
     * transform, popup anchor coordinates, and opening animation.
     */
    function updateAttachmentMenuPosition() {
        /*
         * Query the wrapper every time. WhatsApp can replace application elements
         * during initialization, reload, or single-page application navigation.
         */
        const scroller =
            document.querySelector(".app-wrapper-web");

        if (scroller === null) {
            return;
        }

        /*
         * data-menu-content is preferable to generated WhatsApp CSS classes and is
         * independent of the display language.
         */
        const menuContents =
            document.querySelectorAll('[data-menu-content="true"]');

        if (menuContents.length === 0) {
            return;
        }

        /*
         * Manual testing confirmed that both offsets must be translated in the
         * positive direction:
         *
         * Portrait test:
         *     scrollLeft approximately 234.8px -> translateX approximately 234.8px
         *
         * Landscape test:
         *     scrollTop 80px -> translateY 80px
         */
        const horizontalOffset =
            Math.abs(scroller.scrollLeft);

        const verticalOffset =
            Math.abs(scroller.scrollTop);

        menuContents.forEach(function (menuContent) {
            menuContent.style.setProperty(
                "translate",
                horizontalOffset + "px "
                        + verticalOffset + "px",
                "important"
            );
        });

        console.log(
            "WWTG attachment menu correction:",
            {
                scrollLeft: scroller.scrollLeft,
                scrollTop: scroller.scrollTop,
                horizontalOffset: horizontalOffset,
                verticalOffset: verticalOffset,
                appliedTranslate:
                    horizontalOffset + "px " + verticalOffset + "px",
                correctedMenus: menuContents.length
            }
        );
    }

    /**
     * Schedules at most one update for the next animation frame.
     */
    function scheduleAttachmentMenuPositionUpdate() {
        const state =
            window.__wwtgAttachmentMenuPositionPatchState;

        if (state === undefined || state.updatePending) {
            return;
        }

        state.updatePending = true;

        window.requestAnimationFrame(function () {
            state.updatePending = false;
            state.update();
        });
    }

    /**
     * Schedules immediate and delayed updates.
     *
     * WhatsApp may create the menu and then reposition the menu during
     * subsequent rendering passes.
     */
    function scheduleMultipleAttachmentMenuPositionUpdates() {
        const state =
            window.__wwtgAttachmentMenuPositionPatchState;

        if (state === undefined) {
            return;
        }

        scheduleAttachmentMenuPositionUpdate();

        window.setTimeout(state.update, 0);
        window.setTimeout(state.update, 50);
        window.setTimeout(state.update, 150);
        window.setTimeout(state.update, 300);
    }

    /*
     * If the script is evaluated again in the same document, refresh the
     * updater without registering duplicate observers and event listeners.
     */
    if (window.__wwtgAttachmentMenuPositionPatchState !== undefined) {
        window.__wwtgAttachmentMenuPositionPatchState.update =
            updateAttachmentMenuPosition;

        console.log(
            "WWTG attachment patch: existing installation refreshed"
        );

        scheduleMultipleAttachmentMenuPositionUpdates();
        return;
    }

    /*
     * Store only durable state. DOM elements are deliberately not stored
     * because WhatsApp may replace those elements later.
     */
    window.__wwtgAttachmentMenuPositionPatchState = {
        update: updateAttachmentMenuPosition,
        updatePending: false,
        observer: null
    };

    /*
     * Capture scroll events at document level so the patch also handles a
     * replacement ".app-wrapper-web" element.
     */
    document.addEventListener(
        "scroll",
        function (event) {
            const target = event.target;

            if (
                target instanceof Element &&
                target.matches(".app-wrapper-web")
            ) {
                scheduleAttachmentMenuPositionUpdate();
            }
        },
        {
            capture: true,
            passive: true
        }
    );

    /*
     * The menu is inserted asynchronously after the pointer or click event.
     * Schedule several corrections to cover WhatsApp's rendering passes.
     */
    document.addEventListener(
        "pointerup",
        scheduleMultipleAttachmentMenuPositionUpdates,
        {
            capture: true,
            passive: true
        }
    );

    document.addEventListener(
        "click",
        scheduleMultipleAttachmentMenuPositionUpdates,
        {
            capture: true,
            passive: true
        }
    );

    /*
     * Observe the document element for newly inserted or replaced popup
     * elements and application containers.
     */
    const observer = new MutationObserver(function (mutations) {
        for (const mutation of mutations) {
            if (
                mutation.addedNodes.length > 0 ||
                mutation.removedNodes.length > 0
            ) {
                scheduleAttachmentMenuPositionUpdate();
                return;
            }
        }
    });

    observer.observe(
        document.documentElement,
        {
            childList: true,
            subtree: true
        }
    );

    window.__wwtgAttachmentMenuPositionPatchState.observer =
        observer;

    console.log(
        "WWTG attachment patch: resilient listeners installed"
    );

    scheduleMultipleAttachmentMenuPositionUpdates();
})();
