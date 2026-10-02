(
	/* used in WebviewActivity.java, see setContentSize() */
	function() {
		console.log('cleanMetaAI installed');
		var d = document;
		function cleanMetaAI() {
			if (!d.documentElement) {
				console.log('cleanMetaAI execution deferred: document root unavailable');
				return;
			}
			console.log('cleanMetaAI running');
			try {
				var hide = function(el) {
					if (!el)
						return;
					el.style.setProperty('display', 'none', 'important');
					var curr = el;
					for (var depth = 0; depth < 5; depth++) {
						var p = curr.parentElement;
						if (!p || p === d.body || p === d.documentElement || p.id === 'app' || p.id === 'main' || p.id === 'side')
							break;
						if (p.children.length === 1) {
							p.style.setProperty('display', 'none', 'important');
							curr = p;
						} else {
							break;
						}
					}
				};
				var els = d.querySelectorAll('[aria-label*="Meta AI" i], [title*="Meta AI" i], [id*="MetaAi" i], [clip-path*="MetaAi" i], [fill*="MetaAi" i]');
				console.log('cleanMetaAI attribute candidates:', els.length);
				for (var i = 0; i < els.length; i++) {
					var host = els[i].closest('button, [role="button"], [data-navbar-item], [data-tab]') || els[i];
					hide(host);
				}
				var titles = d.querySelectorAll('title, svg title');
				console.log('cleanMetaAI title candidates:', titles.length);
				for (var t = 0; t < titles.length; t++) {
					if (/meta[ -]?ai/i.test(titles[t].textContent || '')) {
						var hostTitle = titles[t].closest('button, [role="button"], [data-navbar-item], [data-tab], svg') || titles[t];
						hide(hostTitle);
					}
				}
				var textMatches = 0;
				var spans = d.querySelectorAll('span, div, button, p');
				console.log('cleanMetaAI spans candidates:', spans.length);
				for (var s = 0; s < spans.length; s++) {
					var node = spans[s];
					if (node.childNodes.length === 1 && node.childNodes[0].nodeType === Node.TEXT_NODE) {
						var txt = node.textContent.trim();
						if (/^Ask Meta AI$/i.test(txt) || /^Meta AI$/i.test(txt)) {
							textMatches++;
							var hostText = node.closest('button, [role="button"], [data-navbar-item], [data-tab]') || node;
							hide(hostText);
						}
					}
				}
				console.log('cleanMetaAI text candidates:', textMatches);
			} catch (e) {
				console.error('cleanMetaAI error', e);
			}
		}
		/* Installs the Meta AI observer when the document root becomes available. The observer is stored globally only after observe() succeeds. This avoids leaving a nonfunctional observer object behind after an early installation. */
		function installCleanMetaAIObserver() {
			/* Reuse the existing observer if a previous injection already installed one successfully. */
			if (window.__wwtgObserver) {
				console.log('cleanMetaAI observer already installed');
				return;
			}
			var observerTarget = d.documentElement;
			/* evaluateJavascript() can run before document.documentElement exists. Retry shortly rather than passing null to MutationObserver.observe(). */
			if (!observerTarget) {
				console.log('cleanMetaAI observer installation deferred: ' + 'document root unavailable');
				window.setTimeout(installCleanMetaAIObserver, 100);
				return;
			}
			var cleaningScheduled = false;
			/* Keep the observer local until observe() has succeeded. */
			var observer = new MutationObserver(function() {
				/* WhatsApp can create many mutation records during one render. Run the cleaner at most once per animation frame. */
				if (cleaningScheduled) {
					return;
				}
				cleaningScheduled = true;
				window.requestAnimationFrame(function() {
					cleaningScheduled = false;
					cleanMetaAI();
				});
			}
			);
			try {
				/* Watch for React adding or replacing DOM elements. Do not observe style attributes here because cleanMetaAI() changes style attributes itself. Observing those changes would cause the observer to react to its own modifications. */
				observer.observe(observerTarget, {
					childList: true,
					subtree: true
				});
				/* Publish the observer only after successful installation. */
				window.__wwtgObserver = observer;
				console.log('cleanMetaAI observer installed');
				/* Run again now that the document root is known to exist. */
				cleanMetaAI();
			} catch (e) {
				observer.disconnect();
				console.error('cleanMetaAI observer installation error', e);
				/* The document may have been replaced between retrieving the target and calling observe(). Retry the installation. */
				window.setTimeout(installCleanMetaAIObserver, 100);
			}
		}
		/* Perform an immediate pass when possible. The function safely returns if the document root does not exist yet. */
		cleanMetaAI();
		/* Install the observer now or defer installation until the DOM root exists. */
		installCleanMetaAIObserver();
	}
)();
