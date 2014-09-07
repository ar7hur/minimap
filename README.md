# Minimap

A minimalist and partial IMAP client in native Clojure.
- Does not rely on javax.mail
- Supports Gmail IMAP extensions (search, thread IDs, message IDs)

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
