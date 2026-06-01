&nbsp;
<p align="center">
  <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://cdn.adguard.com/public/Adguard/Common/Logos/cont_dark.svg" width="300px" alt="AdGuard Content Blocker" />
   <img src="https://cdn.adguard.com/public/Adguard/Common/Logos/cont.svg" width="300px" alt="AdGuard Content Blocker" />
 </picture>
</p>
<h3 align="center">Ad blocker app to block ads in Yandex Browser and Samsung Internet browser</h3>
<p align="center">
    <a href="https://adguard.com/">Website</a> |
    <a href="https://reddit.com/r/Adguard">Reddit</a> |
    <a href="https://twitter.com/AdGuard">Twitter</a> |
    <a href="https://t.me/adguard_en">Telegram</a>
</p>


> ### Disclaimer
> - AdGuard Content Blocker is a free app. We believe that all free products should be open source, and AdGuard Content Blocker is not an exception. Its code can be found in this repository.
> - Privacy policy: https://adguard.com/privacy/content-blocker.html (Briefly: AdGuard sends only necessary, limited information, e.g. information required for filter updates).

&nbsp;


AdGuard Content Blocker is a tool to block ads in browsers that support content blocking technology. Currently, these browsers are Samsung Internet and Yandex Browser.

## uBlock Origin filter compatibility

This app accepts common uBlock Origin-style static filter syntax in user rules and imported lists. There are two execution modes:

- External browser mode exports normalized rules to Samsung Internet and Yandex Browser. In this mode the target browser's content-blocker engine decides which normalized network, redirect, popup, scriptlet, cosmetic, and parameter-removal rules can run.
- Advanced Browser mode is the app's own WebView runtime. It evaluates a broader uBO-like subset directly inside the app and keeps a bounded diagnostics log of block, redirect, exception, parameter-removal, cosmetic, and scriptlet decisions.

Supported compatibility categories in the export path:

- Network URL patterns: plain patterns, wildcard patterns, hostname anchors such as `||example.com^`, and regex-delimited rules such as `/adserver\d+\.js/`.
- Context options: common request type and context options such as `$script`, `$image`, `$stylesheet`, `$css`, `$subdocument`, `$frame`, `$xmlhttprequest`, `$xhr`, `$third-party`, `$3p`, `$1p`, `$domain=...`, `$method=...`, `$match-case`, `$popup`, `$redirect=...`, and `$removeparam=...`. Custom and engine-specific network options are preserved so custom filters are not disabled by the compatibility layer.
- Cosmetic filters: standard CSS selector hiding and exceptions such as `example.com##.ad` and `example.com#@#.ad`.
- Scriptlet aliases: common uBO `##+js(...)` aliases are converted to AdGuard scriptlet syntax for `set`, `set-constant`, `aopr`, `abort-on-property-read`, `aopw`, `abort-on-property-write`, `acis`, `abort-current-inline-script`, `ra`, `remove-attr`, `rc`, `remove-class`, `noeval`, `aeld`, `nostif`, and `nosiif`.

Advanced Browser adds best-effort runtime support for:

- `$important`, `$badfilter`, exception rules, request type matching, `$method=...`, case-insensitive URL matching by default, `$match-case`, `$third-party`, `$3p`, `$1p`, `$~third-party`, `$~3p`, `$~1p`, and `$domain=...` context.
- `$redirect=` and `$redirect-rule=` resources such as `noopjs`, `noopcss`, `nooptext`, `noopjson`, `noophtml`, `noopframe`, `noopvast-*`, `noopvmap-1.0`, `empty`, transparent `1x1.gif`, and common neutered analytics/ad-tech scripts.
- `$popup` navigation blocking inside the WebView.
- `$removeparam=` with request-pattern and page-domain context, exception rules, exact parameter names, wildcard names, and regex names.
- Static cosmetic filtering, `$elemhide`/`$generichide` cosmetic exceptions, procedural cosmetic filters for `:has-text`, `:matches-attr`, `:matches-css`, and `:xpath`, and common safe scriptlets including remove-attr/remove-class, abort-on-property-read/write/current-inline-script, `noeval`, addEventListener defusing, and timer defusing.

Runtime limitations:

- The app is a content-blocker provider. It does not run a browser extension runtime and cannot directly inspect browser tabs, close popup windows, rewrite browser requests, or inject JavaScript by itself.
- Redirect, popup, scriptlet, and tracking-parameter removal rules work only when the target browser's content-blocker engine supports the normalized rule syntax.
- Unsupported procedural cosmetic filters and unknown scriptlets are written as `! ubo-unsupported: ...` comments in `filters.txt` instead of being silently dropped.
- Full uBO dynamic filtering UI, per-site switches UI, CSP/header mutation, and HTML response filtering are deferred until the app has browser/request APIs that can support those behaviors correctly.

To get more information and to download AdGuard Content Blocker, visit our website [https://adguard.com/](https://adguard.com/adguard-content-blocker/overview.html).

&nbsp;

<p align="center">
<img src="https://cdn.adguard.com/content/github/content_blocker/android/welcome.png" width="250">
<img src="https://cdn.adguard.com/content/github/content_blocker/android/main.png" width="250">
<img src="https://cdn.adguard.com/content/github/content_blocker/android/filters.png" width="250">
</p>

&nbsp;


### Our plans

To see the 'big picture', to watch current progress and to get an idea of approximate dates for upcoming AdGuard Content Blocker releases, see this page: https://github.com/AdguardTeam/ContentBlocker/milestones

### Releases

[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png" alt="Get it on Google Play" height="70">](https://play.google.com/store/apps/details?id=com.adguard.android.contentblocker)
[<img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="70">](https://f-droid.org/packages/com.adguard.android.contentblocker/)

You can also find all AdGuard Content Blocker releases here: https://github.com/AdguardTeam/ContentBlocker/releases

### How to report an issue?

GitHub can be used to report a bug or to submit a feature request. To do so, go to [this page](https://github.com/AdguardTeam/ContentBlocker/issues) and click the *New issue* button. Please, try to use the template that will be provided for you to report bugs (unless your issue is unique enough to justify deviating from the template).

>**Note:** for the filter-related issues (missed ads, false positives etc.) use the [online reporting tool](https://reports.adguard.com/new_issue.html). 

### Becoming a beta tester

To become a beta tester, go to Content Blocker's [Google Play page](https://play.google.com/store/apps/details?id=com.adguard.android.contentblocker) and scroll down to the "Become a beta tester" block. Tap the "I'm in" button to switch to beta channel. You will have an option to leave the beta test at any time the same way.

### Translating into other languages

You can help us translate Content Blocker into other languages. Everything you need to know about AdGuard translations is gathered in this [KB article](https://kb.adguard.com/general/adguard-translations). Once you are ready, head to [CrowdIn website](https://crowdin.com/project/adguard-applications/en#/adguard-content-blocker) and start translating — even if the translation already exists, you can always suggest a better variant.
