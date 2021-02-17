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

    const me = this;
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
    this._toggleLanguageButton = this._multilineHeader.querySelector('.mls-toggle-language-button');
    this._toggleLanguageOptions = this._multilineHeader.querySelector('.toggle-language-data');
    this._multilineContent = this._wrapper.querySelector('.mls-tab-content');
    this.MAX_TABS_VISIBLE = 3;
    this.mobileOrSmallScreen = window.matchMedia('(max-width: 600px)').matches;

    this.renderMultilineHeaderAndContent();

    const langTabs = this._toggleLanguageOptions.querySelectorAll('li');
    if (langTabs.length > 0) {
        const element = langTabs[0];
        const langCode = element.dataset.lang;
        const _li = this.renderLanguageOptionReplacement(langCode);
        this.replaceButtonCaption(_li);
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
    _li.classList.add('nav-item');
    if (active) {
        _li.classList.add('active');
    }

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
        const langName = me.getLanguageName(langCode);

        if (!me.languageManagementEnabled || langCode === me.FALLBACK_CODE || me.values[langCode]) {
            const _row = me.renderLanguageRow(langCode);
            me._modalInputs.appendChild(_row);
            rowsAdded = true;
        }
    });

    if (this.languageManagementEnabled) {
        if (!rowsAdded) {
            this._modalPlaceholder.classList.remove('hidden');
        }

        const _addLanguageButton = this._modal.querySelector('.mls-add-language-button');
        _addLanguageButton.classList.remove('hidden');

        const _addLanguageOptions = _addLanguageButton.querySelector('.dropdown-menu');

        this.forEachValidLanguage(function (langCode) {
            const _language = document.createElement('li');
            _language.classList.add('pointer');
            _language.dataset.lang = langCode;

            const _link = me.renderLanguageLink(langCode);

            _link.addEventListener('click', function () {
                const _row = me.renderLanguageRow(langCode);
                me._modalInputs.appendChild(_row);
                me._modalPlaceholder.classList.add('hidden');
                me.updateLanguageManagementOptions();
            });
            _language.appendChild(_link);
            _addLanguageOptions.appendChild(_language);
        });
    }
}

MultiLanguageField.prototype.countVisibleLanguageTabs = function () {
    return this._multilineHeader.querySelectorAll('ul > li.nav-item > a.mls-language-label').length;
}

MultiLanguageField.prototype.shouldRenderDropdownInsteadOfTabs = function () {
    if (this.mobileOrSmallScreen) {
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

    const _caretSpan = document.createElement('span');
    _caretSpan.classList.add('caret');
    _anchor.appendChild(_caretSpan);

    return _li;
}

MultiLanguageField.prototype.renderMultilineHeaderAndContent = function () {
    let rowsAdded = false;
    const me = this;
    this.forEachValidLanguage(function (langCode) {
        const langName = me.getLanguageName(langCode);

        if (!me.languageManagementEnabled || langCode === me.FALLBACK_CODE || me.values[langCode]) {
            const active = !rowsAdded;

            const _languageLi = me.buildLanguageEntry(langCode, true);
            if (active) {
                _languageLi.classList.add('active');
            }
            me._toggleLanguageOptions.appendChild(_languageLi);

            const _langTab = me.renderLanguageTab(langCode, active);
            me._toggleLanguageButton.parentNode.insertBefore(_langTab, me._toggleLanguageButton);

            const _langTabInput = me.renderLanguageTabInput(langCode, active);
            me._multilineContent.appendChild(_langTabInput);

            if (me.shouldRenderDropdownInsteadOfTabs()) {
                me.hideAllLanguageTabs();
                me._toggleLanguageButton.classList.remove('hidden');
            }

            rowsAdded = true;
        }
    });

    if (this.languageManagementEnabled) {
        const _addLanguageButton = this._multilineHeader.querySelector('.mls-add-language-button');
        _addLanguageButton.classList.remove('hidden');

        const _addLanguageOptions = _addLanguageButton.querySelector('.dropdown-menu');

        const me = this;
        this.forEachValidLanguage(function (langCode) {
            const _languageLiDropdown = me.buildLanguageEntry(langCode, false);
            _languageLiDropdown.querySelector('a').addEventListener('click', function () {
                const _langTab = me.renderLanguageTab(langCode, true);
                me._toggleLanguageButton.parentNode.insertBefore(_langTab, me._toggleLanguageButton);

                const _languageLi = me.buildLanguageEntry(langCode, true);
                _languageLi.classList.add('active');
                me._toggleLanguageOptions.appendChild(_languageLi);

                const _languageTabInput = me.renderLanguageTabInput(langCode, true);
                me._multilineContent.appendChild(_languageTabInput);

                if (me.shouldRenderDropdownInsteadOfTabs()) {
                    me.hideAllLanguageTabs();
                    me._toggleLanguageButton.classList.remove('hidden');
                    me._toggleLanguageButton.classList.add('active');
                }

                const _li = me.renderLanguageOptionReplacement(langCode);
                me.replaceButtonCaption(_li);

                me.updateLanguageManagementOptions();
            });
            _addLanguageOptions.appendChild(_languageLiDropdown);
        });
    }
}

MultiLanguageField.prototype.hideAllLanguageTabs = function () {
    this._multilineHeader.querySelectorAll('ul > li.nav-item > a.mls-language-label').forEach(function (item) {
        item.parentElement.classList.remove('active');
        item.parentElement.classList.add('hidden');
    });
}

MultiLanguageField.prototype.buildLanguageEntry = function (langCode, syncDropdownTitleOnClick) {
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

    if (syncDropdownTitleOnClick) {
        const me = this;
        _link.addEventListener('click', function () {
            const _li = me.renderLanguageOptionReplacement(langCode);
            me.replaceButtonCaption(_li);
        });
    }

    return _languageLi;
}

MultiLanguageField.prototype.replaceButtonCaption = function (element) {
    const _oldPlaceholder = this._toggleLanguageButton.querySelector('.toggle-language-placeholder');
    this._toggleLanguageButton.classList.add('active');
    const _newPlaceholder = document.createElement('div');
    _newPlaceholder.classList.add('toggle-language-placeholder');
    _newPlaceholder.appendChild(element);
    _oldPlaceholder.parentElement.replaceChild(_newPlaceholder, _oldPlaceholder);
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

    let _addLanguageButton;
    if (this.multiline) {
        _addLanguageButton = this._multilineHeader.querySelector('.mls-add-language-button');
    } else {
        _addLanguageButton = this._modal.querySelector('.mls-add-language-button');
    }

    const _addLanguageOptions = _addLanguageButton.querySelector('.dropdown-menu');

    if (this.multiline) {
        this._multilineContent.querySelectorAll('.tab-pane').forEach(function (_pane) {
            const lang = _pane.querySelector('textarea').dataset.lang;
            const _langOption = _addLanguageOptions.querySelector('li[data-lang="' + lang + '"]');
            if (_langOption) {
                _langOption.classList.add('hidden');
            }
        });
    } else {
        this._modalInputs.querySelectorAll('.row').forEach(function (_row) {
            const lang = _row.querySelector('input').dataset.lang;
            const _langOption = _addLanguageOptions.querySelector('li[data-lang="' + lang + '"]');
            if (_langOption) {
                _langOption.classList.add('hidden');
            }
        });
    }

    const _selectableOption = _addLanguageOptions.querySelector('li:not(.hidden)');
    if (!_selectableOption) {
        _addLanguageButton.classList.add('hidden');
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
        _globe.src = '/assets/images/flags/globe.png';
        return _globe;
    } else {
        const _flag = document.createElement('img');
        _flag.classList.add('mls-language-flag');
        _flag.src = '/assets/images/flags/' + langCode + '.png';
        _flag.alt = langCode;
        _flag.title = langCode;
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
