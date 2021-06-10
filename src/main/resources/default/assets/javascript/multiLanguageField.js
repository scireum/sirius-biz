/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

function MultiLanguageField(options) {
    this.FALLBACK_CODE = 'fallback';

    this.defaultLanguage = options.defaultLanguage;
    this.fieldName = options.fieldName;
    this.hasFallback = options.hasFallback || false;
    this.fallbackLabel = options.fallbackLabel;
    this.wrapperId = options.wrapperId;
    this.modalId = options.modalId;
    this.values = options.values;
    this.languageManagementEnabled = options.languageManagementEnabled;
    this.rows = options.rows;
    this.multiline = options.multiline || false;
    this.mobileOrSmallScreen = window.matchMedia('(max-width: 600px)').matches;

    this.validLanguages = {};
    if (this.hasFallback) {
        this.validLanguages.fallback = this.fallbackLabel;
    }

    const me = this;
    Object.keys(options.validLanguages).forEach(function (langCode) {
        me.validLanguages[langCode] = options.validLanguages[langCode];
    });

    this._wrapper = document.getElementById(this.wrapperId);
    this._input = this._wrapper.querySelector('.mls-input');

    if (this.multiline) {
        this.buildMultiline();
    } else {
        this.buildSingleline();
    }
}

MultiLanguageField.prototype.buildSingleline = function () {
    this._hiddenInputs = this._wrapper.querySelector('.mls-hidden-inputs');
    this._modal = document.getElementById(this.modalId);
    this._modalInputs = this._modal.querySelector('.mls-modal-inputs');
    this._modalPlaceholder = this._modal.querySelector('.mls-modal-placeholder');
    this._addLanguageButton = this._modal.querySelector('.mls-add-language-button');
    this._addLanguageOptions = this._addLanguageButton.querySelector('.dropdown-menu');

    if (this.mobileOrSmallScreen) {
        this._addLanguageOptions.classList.add('dropdown-menu-right');
    }

    this._modalBody = this._modal.querySelector('.modal-body');
    this._modalContent = this._modal.querySelector('.modal-content');

    const me = this;
    this._addLanguageButton.addEventListener('click', function () {
        const langOptionCount = me._addLanguageOptions.querySelectorAll('li:not(.hidden)').length;
        // 12px are reserved for border and padding of the language selection menu
        const totalRowsHeight = (langOptionCount * 27) + 12;

        if (totalRowsHeight > me._modalBody.clientHeight) {
            me._addLanguageOptions.style.maxHeight = me._modalBody.clientHeight + 'px';
        } else {
            me._addLanguageOptions.style.maxHeight = totalRowsHeight + 'px';
        }
    });

    // have to use jquery here as bootstrap modals only trigger jquery events
    $(me._modal).on('hidden.bs.modal', function () {
        me.updateHiddenFields();
        me.updateOuterInputField();
    })

    this.renderModalBody();
    this.updateHiddenFields();
    this.updateOuterInputField();
    this.updateLanguageManagementOptions();
}

MultiLanguageField.prototype.buildMultiline = function () {
    this._multilineHeader = this._wrapper.querySelector('.mls-tab-header');
    this._addLanguageButton = this._multilineHeader.querySelector('.mls-add-language-button');
    this._addLanguageOptions = this._addLanguageButton.querySelector('.dropdown-menu');
    this._toggleLanguageButton = this._multilineHeader.querySelector('.mls-toggle-language-button');
    this._toggleLanguageOptions = this._multilineHeader.querySelector('.toggle-language-data');
    this._multilineContent = this._wrapper.querySelector('.mls-tab-content');
    this.MAX_TABS_VISIBLE = 3;

    if (this.mobileOrSmallScreen) {
        this._addLanguageOptions.classList.add('dropdown-menu-right');
        this._toggleLanguageOptions.classList.add('dropdown-menu-right');
    }

    this.renderMultilineHeaderAndContent();

    const langTabs = this._toggleLanguageOptions.querySelectorAll('li');
    if (langTabs.length > 0) {
        const element = langTabs[0];
        const langCode = element.dataset.lang;
        this.markLanguageItemAsSelected(langCode);
        this.updateLanguageSwitcherLabel(langCode);
    }

    this.updateLanguageManagementOptions();
}

MultiLanguageField.prototype.renderLanguageRow = function (langCode) {
    const _row = document.createElement('div');
    _row.classList.add('row');
    _row.classList.add('form-group');

    const _labelColumn = document.createElement('div');
    _labelColumn.classList.add('col-md-3');
    _labelColumn.classList.add('mls-language-label');
    _row.appendChild(_labelColumn);

    const _flag = this.renderFlag(langCode);
    _labelColumn.appendChild(_flag);

    const _name = document.createElement('span');
    _name.textContent = this.getLanguageName(langCode);
    _labelColumn.appendChild(_name);

    const _inputColumn = document.createElement('div');
    _inputColumn.classList.add('col-md-9');
    _row.appendChild(_inputColumn);

    const _textInput = document.createElement('input');
    _textInput.classList.add('form-control');
    _textInput.type = 'text';
    _textInput.dataset.lang = langCode;
    _textInput.value = this.values[langCode] || '';
    _inputColumn.appendChild(_textInput);

    return _row;
};

MultiLanguageField.prototype.renderLanguageTab = function (langCode, active) {
    const _li = document.createElement('li');
    _li.classList.add('mls-language-tab');
    if (active) {
        _li.classList.add('active');
    }
    // Add language code for tab handling (eg. active style toggling)
    _li.dataset.lang = langCode;

    const _anchor = this.renderLanguageLink(langCode);
    _li.appendChild(_anchor);

    return _li;
};

MultiLanguageField.prototype.renderLanguageTabInput = function (langCode, active) {
    const _inputColumn = document.createElement('div');
    _inputColumn.classList.add('tab-pane');
    if (active) {
        _inputColumn.classList.add('active');
    }
    _inputColumn.id = this.fieldName + '-' + langCode;

    const _textArea = document.createElement('textarea');
    _textArea.classList.add('form-control');
    _textArea.classList.add('input-block-level');
    _textArea.classList.add('mls-input');
    _textArea.rows = this.rows;

    if (langCode === this.FALLBACK_CODE) {
        _textArea.name = this.fieldName;
    } else {
        _textArea.name = this.fieldName + '-' + langCode;
    }

    _textArea.dataset.lang = langCode;
    _textArea.value = this.values[langCode] || '';
    _inputColumn.appendChild(_textArea);

    return _inputColumn;
};

MultiLanguageField.prototype.renderModalBody = function () {
    this._modal.classList.add('mls-modal');

    let rowsAdded = false;
    const me = this;
    this.forEachValidLanguage(function (langCode) {
        if (!me.languageManagementEnabled || langCode === me.FALLBACK_CODE || me.values[langCode]) {
            const _row = me.renderLanguageRow(langCode);
            me._modalInputs.appendChild(_row);
            rowsAdded = true;
        }
    });

    if (this.languageManagementEnabled) {
        if (!rowsAdded) {
            this.showModalLanguagePlaceholder();
        }

        this.showAddLanguageButton();

        this.forEachValidLanguage(function (langCode) {
            const _language = document.createElement('li');
            _language.classList.add('pointer');
            _language.dataset.lang = langCode;

            const _link = me.renderLanguageLink(langCode);

            _link.addEventListener('click', function () {
                const _row = me.renderLanguageRow(langCode);
                me._modalInputs.appendChild(_row);
                me.hideModalLanguagePlaceholder();
                me.updateLanguageManagementOptions();
            });
            _language.appendChild(_link);
            me._addLanguageOptions.appendChild(_language);
        });
    }
}

MultiLanguageField.prototype.countVisibleLanguageTabs = function () {
    return this._multilineHeader.querySelectorAll('li.mls-language-tab').length;
}

MultiLanguageField.prototype.removeAllLanguageTabs = function () {
    this._multilineHeader.querySelectorAll('li.mls-language-tab').forEach(function (item) {
        item.parentNode.removeChild(item);
    });
}

MultiLanguageField.prototype.showModalLanguagePlaceholder = function () {
    this._modalPlaceholder.classList.remove('hidden');
}

MultiLanguageField.prototype.hideModalLanguagePlaceholder = function () {
    this._modalPlaceholder.classList.add('hidden');
}

MultiLanguageField.prototype.showAddLanguageButton = function () {
    this._addLanguageButton.classList.remove('hidden');
}

MultiLanguageField.prototype.hideAddLanguageButton = function () {
    this._addLanguageButton.classList.add('hidden');
}

MultiLanguageField.prototype.showLanguageToggleButton = function () {
    this._toggleLanguageButton.classList.remove('hidden');
}

MultiLanguageField.prototype.shouldRenderDropdownInsteadOfTabs = function () {
    if (this.mobileOrSmallScreen) {
        return true;
    }
    if (!this._toggleLanguageButton.classList.contains('hidden')) {
        return true;
    }
    if (this.countVisibleLanguageTabs() > this.MAX_TABS_VISIBLE) {
        return true;
    }
    return false;
}

MultiLanguageField.prototype.renderLanguageLink = function (langCode) {
    const _anchor = document.createElement('a');
    _anchor.classList.add('nav-link');
    _anchor.classList.add('mls-language-label');
    _anchor.href = '#' + this.fieldName + '-' + langCode;
    _anchor.dataset.toggle = 'tab';

    const _flag = this.renderFlag(langCode);
    _anchor.appendChild(_flag);

    const _name = document.createElement('span');
    _name.textContent = this.getLanguageName(langCode);
    _anchor.appendChild(_name);

    return _anchor;
}

MultiLanguageField.prototype.renderLanguageOptionReplacement = function (langCode) {
    const _li = document.createElement('li');
    _li.classList.add('nav-item');
    _li.classList.add('active');

    const _anchor = this.renderLanguageLink(langCode);
    _li.appendChild(_anchor);

    const _caretSpan = this.renderCaretSpan();
    _anchor.appendChild(_caretSpan);

    return _li;
}

MultiLanguageField.prototype.renderCaretSpan = function () {
    const _caretSpan = document.createElement('span');
    _caretSpan.classList.add('caret');
    return _caretSpan;
}

MultiLanguageField.prototype.addLanguage = function (langCode, active) {
    // Render language management option for adding the language
    const _toggleLanguageOption = this.buildLanguageEntry(langCode);
    this._toggleLanguageOptions.appendChild(_toggleLanguageOption);
    // Mark the element as selected in the list of language entries
    this.markLanguageItemAsSelected(langCode);

    // Render the actual tab which triggers the associated language pane
    const _langTab = this.renderLanguageTab(langCode, active);
    this._toggleLanguageButton.parentNode.insertBefore(_langTab, this._toggleLanguageButton);

    // Render the actual input associated with the created tab
    const _langTabInput = this.renderLanguageTabInput(langCode, active);
    this._multilineContent.appendChild(_langTabInput);
}


MultiLanguageField.prototype.renderMultilineHeaderAndContent = function () {
    const me = this;

    this.forEachValidLanguage(function (langCode) {
        if (!me.languageManagementEnabled || langCode === me.FALLBACK_CODE || me.values[langCode]) {
            me.addLanguage(langCode, false)
        }
    });

    // Activate the first option/tab and its corresponding pane
    if (me.shouldRenderDropdownInsteadOfTabs()) {
        me.removeAllLanguageTabs();
        me.showLanguageToggleButton();
    } else {
        // Set the current tab active
        this._multilineHeader.querySelector('li.mls-language-tab').classList.add('active');
    }
    // Set the corresponding tab content active
    me._multilineContent.querySelector('.tab-pane').classList.add('active');

    if (this.languageManagementEnabled) {
        this.showAddLanguageButton();

        const me = this;
        this.forEachValidLanguage(function (langCode) {
            const _additionalLanguageOption = me.buildAddLanguageEntry(langCode);
            _additionalLanguageOption.querySelector('a').addEventListener('click', function () {
                me.addLanguage(langCode, true);

                // Remove the active class from all tabs not being active after adding the new language option
                me._multilineHeader.querySelectorAll('li.mls-language-tab').forEach(function (_tab) {
                    if (_tab.dataset.lang !== langCode) {
                        _tab.classList.remove('active');
                    }
                });
                // Remove the active class from all tab panes not being active after adding the new language option
                me._multilineContent.querySelectorAll('div.tab-pane>textarea').forEach(function (_textarea) {
                    if (_textarea.dataset.lang !== langCode) {
                        _textarea.parentElement.classList.remove('active');
                    }
                });

                if (me.shouldRenderDropdownInsteadOfTabs()) {
                    me.removeAllLanguageTabs();
                    me.showLanguageToggleButton();
                }

                me.updateLanguageSwitcherLabel(langCode);

                me.updateLanguageManagementOptions();
            });
            me._addLanguageOptions.appendChild(_additionalLanguageOption);
        });
    }
}

MultiLanguageField.prototype.buildLanguageEntry = function (langCode) {
    const _link = document.createElement('a');
    _link.href = '#' + this.fieldName + '-' + langCode;
    _link.dataset.toggle = 'tab';

    const _flag = this.renderFlag(langCode);
    _link.appendChild(_flag);
    const _text = document.createTextNode(this.getLanguageName(langCode));
    _link.appendChild(_text);
    const _languageLi = document.createElement('li');
    _languageLi.classList.add('pointer');
    _languageLi.dataset.lang = langCode;
    _languageLi.appendChild(_link);

    const me = this;
    _link.addEventListener('click', function () {
        me.updateLanguageSwitcherLabel(langCode);
        // Mark the element as selected in the list of language entries
        me.markLanguageItemAsSelected(langCode);
    });

    return _languageLi;
}

MultiLanguageField.prototype.buildAddLanguageEntry = function (langCode) {
    const _link = document.createElement('a');

    const _flag = this.renderFlag(langCode);
    _link.appendChild(_flag);
    const _text = document.createTextNode(this.getLanguageName(langCode));
    _link.appendChild(_text);
    const _languageLi = document.createElement('li');
    _languageLi.classList.add('pointer');
    _languageLi.dataset.lang = langCode;
    _languageLi.appendChild(_link);

    return _languageLi;
}

MultiLanguageField.prototype.updateLanguageSwitcherLabel = function (langCode) {
    const _dropdownToggle = this._toggleLanguageButton.querySelector('.dropdown-toggle');
    _dropdownToggle.textContent = '';

    const _anchor = this.renderLanguageLink(langCode);

    const _caretSpan = this.renderCaretSpan();
    _anchor.appendChild(_caretSpan);

    _dropdownToggle.appendChild(_anchor);
    this._toggleLanguageButton.classList.add('active');
}

MultiLanguageField.prototype.markLanguageItemAsSelected = function (langCode) {
    // add custom selection class 'language-selected' as Bootstrap does not allow multiple active elements in the same nav
    this._toggleLanguageOptions.querySelectorAll('ul>li.pointer').forEach(function (_li) {
        if (_li.dataset.lang === langCode) {
            _li.classList.add('language-selected');
        } else {
            _li.classList.remove('language-selected');
        }
    });
}

MultiLanguageField.prototype.updateOuterInputField = function () {
    const _fallbackInput = this._modal.querySelector('input[data-lang="fallback"]');
    const _defaultLanguageInput = this._modal.querySelector('input[data-lang="' + this.defaultLanguage + '"]');
    let text;
    if (_defaultLanguageInput) {
        text = _defaultLanguageInput.value;
    }
    if (!text && _fallbackInput) {
        text = _fallbackInput.value;
    }
    this._input.value = text || "";
}

MultiLanguageField.prototype.updateHiddenFields = function () {
    this._hiddenInputs.textContent = '';

    const me = this;
    this._modal.querySelectorAll('input[data-lang]').forEach(function (input) {
        const lang = input.dataset.lang;

        const _hiddenInput = document.createElement('input');
        _hiddenInput.type = 'hidden';
        _hiddenInput.value = input.value;
        _hiddenInput.name = me.fieldName;

        if (lang !== me.FALLBACK_CODE) {
            _hiddenInput.name += '-' + lang;
        }

        me._hiddenInputs.appendChild(_hiddenInput);
    })
}

MultiLanguageField.prototype.updateLanguageManagementOptions = function () {
    if (!this.languageManagementEnabled) {
        return;
    }

    const me = this;

    if (this.multiline) {
        this._multilineContent.querySelectorAll('.tab-pane').forEach(function (_pane) {
            const lang = _pane.querySelector('textarea').dataset.lang;
            const _langOption = me._addLanguageOptions.querySelector('li[data-lang="' + lang + '"]');
            if (_langOption) {
                _langOption.classList.add('hidden');
            }
        });
    } else {
        this._modalInputs.querySelectorAll('.row').forEach(function (_row) {
            const lang = _row.querySelector('input').dataset.lang;
            const _langOption = me._addLanguageOptions.querySelector('li[data-lang="' + lang + '"]');
            if (_langOption) {
                _langOption.classList.add('hidden');
            }
        });
    }

    const _selectableOption = me._addLanguageOptions.querySelector('li:not(.hidden)');
    if (!_selectableOption) {
        me.hideAddLanguageButton();
    }
}

/**
 * Returns the translated language name for a given langCode.
 * @param langCode the language code, eg. 'de'
 * @returns {*}
 */
MultiLanguageField.prototype.getLanguageName = function (langCode) {
    return this.validLanguages[langCode];
}

/**
 * Renders a flag symbol based on the given langCode.
 * @param langCode the language code, eg. 'de'
 * @returns {HTMLImageElement}
 */
MultiLanguageField.prototype.renderFlag = function (langCode) {
    if (langCode === this.FALLBACK_CODE) {
        // globe icon
        const _globe = document.createElement('img');
        _globe.classList.add('mls-language-flag');
        _globe.classList.add('mls-language-globe');
        _globe.src = '/assets/images/flags/languages/globe.png';
        return _globe;
    } else {
        const _flag = document.createElement('img');
        _flag.classList.add('mls-language-flag');
        _flag.src = '/assets/images/flags/languages/' + langCode + '.png';
        _flag.alt = langCode;
        _flag.title = langCode;
        // renders the question mark icon ('rest of world') if the requested flag can't be found
        _flag.onerror = function imgError() {
            this.onerror = '';
            this.src = '/assets/images/flags/languages/row.png';
            return true;
        }
        return _flag;
    }
}

/**
 * Iterates all valid languages in a way that is supported by all browsers (even IE 11).
 *
 * @param callback the function to be called for each language code
 */
MultiLanguageField.prototype.forEachValidLanguage = function (callback) {
    Object.keys(this.validLanguages).forEach(callback);
}
