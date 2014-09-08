# Minimap

A minimalist and partial IMAP client in native Clojure.
- Does not rely on javax.mail
- Supports Gmail IMAP extensions (search, thread IDs, message IDs)
- Supports fetching messages without their attachments

Alternatives:
- [Clojure-mail](https://github.com/owainlewis/clojure-mail) is a nice library but it uses javax.mail. As a consequence it does not support Gmail extensions (extended search, threads...) and (to my knowledge) does not support fetching messages without their attachments.
- [gmail-clj](https://github.com/mikeflynn/gmail-clj) is a wrapper for the new Gmail API (not IMAP). So obviously it only works with Gmail. 

## Usage

```Clojure
[minimap "0.1.0-SNAPSHOT"]

(:use [minimap.core])
```

```Clojure
(def session (login :gmail "email@gmail.com" "password"))

(search session {:gmail "in:mylable mysearchquery"})
```

## License

Distributed under the Eclipse Public License, the same as Clojure.
