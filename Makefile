tests:
	npm install
	lein test-cljs
	lein test-refresh :run-once

help:
	@ make -rpn | sed -n -e '/^$$/ { n ; /^[^ ]*:/p; }' | sort | egrep --color '^[^ ]*:'

.PHONY: tests help
