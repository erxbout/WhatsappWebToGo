/* used in WebviewActivity.java, see setContentSize() */
function installFocusPatch(mKeyboardEnabled) {

    // explicit initialization
    window.__wwtgKeyboardBlocked = false;

    /* called from setKeyboardEnabled */
    if(!window.__wwtgSetKeyboardBlocked) {
        console.log("installFocusPatch: __wwtgSetKeyboardBlocked");
        window.__wwtgSetKeyboardBlocked = function(blocked) {
            if(window.__wwtgKeyboardBlocked == blocked) return;
            window.__wwtgKeyboardBlocked = blocked;
            console.log("__wwtgSetKeyboardBlocked: " + blocked);
            if (blocked) {
                try {
                    if (document.activeElement && document.activeElement !== document.body) {
                        document.activeElement.blur();
                    }
                } catch(e){}
            }
        };
    }

    window.__wwtgSetKeyboardBlocked(!mKeyboardEnabled);

    if (window.__wwtgFocusPatched) return;
    window.__wwtgFocusPatched = true;
    console.log("installFocusPatch: __wwtgFocusPatched");

    // focus interceptor script that blocks programmatic .focus() and auto-blurs editable
    // elements ([contenteditable], <input>, <textarea>, [role="textbox"]) unless the focus
    // action originates from a direct, recent user touch/click on that specific input area.

    var _origFocus = HTMLElement.prototype.focus;
    var _lastUserTouchTime = 0;
    var _lastUserTouchTarget = null;
    var onUserTouch = function(e) {
        _lastUserTouchTime = Date.now();
        _lastUserTouchTarget = e.target;
        if (window.__wwtgKeyboardBlocked && isEditable(e.target)) {
            try { e.target.blur(); } catch(err) {}
        } else if (document.activeElement && isEditable(document.activeElement) &&
                    !isDirectUserInteraction(e.target, document.activeElement)) {
            try { document.activeElement.blur(); } catch(err) {}
        }
    };

    var touchEvents = ['pointerdown', 'touchstart', 'mousedown', 'click'];
    for (var te = 0; te < touchEvents.length; te++) {
        window.addEventListener(touchEvents[te], onUserTouch, true);
    }

    function isEditable(el) {
        if (!el) return false;
        var tag = (el.tagName || '').toUpperCase();
        if (tag === 'INPUT' || tag === 'TEXTAREA') return true;
        if (el.isContentEditable) return true;
        if (el.getAttribute) {
            var ce = el.getAttribute('contenteditable');
            if (ce === 'true' || ce === '') return true;
            var r = el.getAttribute('role');
            if (r === 'textbox' || r === 'searchbox') return true;
        }
        return false;
    }

    function isDirectUserInteraction(target, el) {
        if (!target || !el) return false;
        if (target === el || el.contains(target)) return true;
        var btn = target.closest ? target.closest('button, [role=\"button\"], [role=\"menuitem\"], [role=\"tab\"], [aria-haspopup], [data-icon], [data-tab=\"8\"], [data-tab=\"7\"], [data-tab=\"6\"]') : null;
        if (btn && !el.contains(btn)) {
            return false;
        }
        const EDITABLE_SELECTORS =
            '[role="textbox"], ' +
            '[role="searchbox"], ' +
            '[contenteditable="true"], ' +
            '.selectable-text, ' +
            '.copyable-text, ' +
            '[data-testid="conversation-compose-box"], ' +
            '[data-testid="compose-box"], ' +
            '[data-testid="chat-list-search"]';
        var targetBox = target.closest ? target.closest(EDITABLE_SELECTORS) : null;
        var elBox = el.closest ? el.closest(EDITABLE_SELECTORS) : null;
        if (targetBox && elBox && (targetBox === elBox || targetBox.contains(el) || el.contains(targetBox))) {
            return true;
        }
        return false;
    }

    const timeoutMs = 1000;

    HTMLElement.prototype.focus = function() {
        if (isEditable(this)) {
            if (window.__wwtgKeyboardBlocked) {
                return;
            }
            var isRecent = (Date.now() - _lastUserTouchTime) < timeoutMs;
            if (!isRecent || !isDirectUserInteraction(_lastUserTouchTarget, this)) {
                return;
            }
        }
        return _origFocus.apply(this, arguments);
    };

    window.addEventListener('focusin', function(e) {
        var t = e.target;
        if (isEditable(t)) {
            if (window.__wwtgKeyboardBlocked) {
                try { t.blur(); } catch(err) {}
                return;
            }
            var isRecent = (Date.now() - _lastUserTouchTime) < timeoutMs;
            if (!isRecent || !isDirectUserInteraction(_lastUserTouchTarget, t)) {
                try { t.blur(); } catch(err) {}
            }
        }
    }, true);

}
