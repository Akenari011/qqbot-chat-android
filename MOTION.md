# Chat motion

DECIDED: this is a frequently used messaging interface. Animation explains a change in place or state; it must never delay sending, typing, or opening an action.

- Hierarchical navigation (chat/settings and Back): 240 ms, short horizontal travel on the incoming/outgoing detail screen, fade on the underlying screen. Login is a one-time transition.
- List placement, search/reply/emoji disclosure and contextual controls: 180 ms. Tokens live in `ui/theme/Motion.kt`; every list animation uses a stable domain key.
- Top-level conversation filters change the list without a directional page slide.
- Swipe to reply follows the finger, commits only after the threshold, and returns with a critically damped spring that preserves release velocity. This direct-manipulation return is deliberately separate from the 180 ms disclosure tier. The same operation is available from the long-press menu.
- New incoming messages preserve the reader's timeline position. A visible button returns to the latest message; sending deliberately returns to the end.
- Bottom sheets and the drawer use Material's own interruptible motion and dismiss behavior. Sheet selections dispatch after the hide transition, so a reply panel or confirmation dialog cannot overlap the outgoing sheet. No looping decorative animation.
- Compose honors Android's animator duration scale. At scale 0 all labels, send states, selected tabs, reply previews, errors and actions must remain understandable.

Validation is split into JVM behavior tests and Android device checks. Debug builds and local simulations do not establish release frame-time performance.
