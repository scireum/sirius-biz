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

    const modal = new bootstrap.Modal(this._modal);

    // Open modal when input field is focused
    this._input.addEventListener('focusin', () => {
        modal.show(null);
    });

    if (this.mobileOrSmallScreen) {
        this._addLanguageOptions.classList.add('dropdown-menu-end');
    }

    this._modalBody = this._modal.querySelector('.modal-body');
    this._modalContent = this._modal.querySelector('.modal-content');

    this._addLanguageButton.addEventListener('click', () => {
        const langOptionCount = this._addLanguageOptions.querySelectorAll('li:not(.d-none)').length;
        const optionsHeight = 48;
        const menuPadding = 8;
        const menuBorder = 1;
        const totalRowsHeight = (langOptionCount * optionsHeight) + (menuPadding * 2) + (menuBorder * 2);

        if (totalRowsHeight > this._modalBody.clientHeight) {
            this._addLanguageOptions.style.maxHeight = this._modalBody.clientHeight + 'px';
        } else {
            this._addLanguageOptions.style.maxHeight = totalRowsHeight + 'px';
        }
    });

    this._modal.addEventListener('hidden.bs.modal', () => {
        this.updateHiddenFields();
        this.updateOuterInputField();
    });

    this._modal.addEventListener('shown.bs.modal', () => {
        // focus the first input field in the modal
        const _firstInput = this._modalInputs.querySelector('input');
        if (_firstInput) {
            _firstInput.focus();
        }
    });

    this.renderModalBody();
    this.updateHiddenFields();
    this.updateOuterInputField();
    this.updateLanguageManagementOptions();
}

MultiLanguageField.prototype.buildMultiline = function () {
    this._multilineHeader = this._wrapper.querySelector('.mls-tab-list');
    this._addLanguageButton = this._multilineHeader.querySelector('.mls-add-language-button');
    this._addLanguageOptions = this._addLanguageButton.querySelector('.dropdown-menu');
    this._toggleLanguageButton = this._multilineHeader.querySelector('.mls-toggle-language-button');
    this._toggleLanguageOptions = this._multilineHeader.querySelector('.toggle-language-data');
    this._multilineContent = this._wrapper.querySelector('.mls-tab-content');
    this.MAX_TABS_VISIBLE = 3;

    if (this.mobileOrSmallScreen) {
        this._addLanguageOptions.classList.add('dropdown-menu-end');
        this._toggleLanguageOptions.classList.add('dropdown-menu-end');
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
    _row.classList.add('mb-3');

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

    // Add language code for tab handling (eg. active style toggling)
    _li.dataset.lang = langCode;

    const _anchor = this.renderLanguageLink(langCode, active);
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
            _language.classList.add('dropdown-item');
            _language.classList.add('cursor-pointer');
            _language.dataset.lang = langCode;

            const _link = me.renderLanguageLink(langCode, false);

            _language.addEventListener('click', function () {
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
    this._modalPlaceholder.classList.remove('d-none');
}

MultiLanguageField.prototype.hideModalLanguagePlaceholder = function () {
    this._modalPlaceholder.classList.add('d-none');
}

MultiLanguageField.prototype.showAddLanguageButton = function () {
    this._addLanguageButton.classList.remove('d-none');
}

MultiLanguageField.prototype.hideAddLanguageButton = function () {
    this._addLanguageButton.classList.add('d-none');
}

MultiLanguageField.prototype.showLanguageToggleButton = function () {
    this._toggleLanguageButton.classList.remove('d-none');
}

MultiLanguageField.prototype.shouldRenderDropdownInsteadOfTabs = function () {
    if (this.mobileOrSmallScreen) {
        return true;
    }
    if (!this._toggleLanguageButton.classList.contains('d-none')) {
        return true;
    }
    if (this.countVisibleLanguageTabs() > this.MAX_TABS_VISIBLE) {
        return true;
    }
    return false;
}

MultiLanguageField.prototype.renderLanguageLink = function (langCode, active) {
    const _anchor = document.createElement('a');
    if (this.multiline) {
        _anchor.classList.add('nav-link');
    }
    _anchor.classList.add('mls-language-label');
    if (active) {
        _anchor.classList.add('active');
    }
    _anchor.href = '#' + this.fieldName + '-' + langCode;
    _anchor.dataset.bsToggle = 'tab';

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

    const _anchor = this.renderLanguageLink(langCode, true);

    _li.appendChild(_anchor);

    const _caretSpan = this.renderCaretSpan();
    _anchor.appendChild(_caretSpan);

    return _li;
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

    // this method simulates a link on the selected language to propagate the correct state to the tab bar, followed by
    // a simulated click on the dropdown toggle to close the dropdown
    function simulateClickOnSelectedLanguage() {
        const _selectedLanguageEntry = me._wrapper.querySelector("li.language-selected a");
        if (_selectedLanguageEntry) {
            _selectedLanguageEntry.click();
        }
        me._wrapper.querySelector('.dropdown-toggle').click();
    }

    this.forEachValidLanguage(function (langCode) {
        if (!me.languageManagementEnabled || langCode === me.FALLBACK_CODE || me.values[langCode]) {
            me.addLanguage(langCode, false)
        }
    });

    // Activate the first option/tab and its corresponding pane
    if (me.shouldRenderDropdownInsteadOfTabs()) {
        me.removeAllLanguageTabs();
        me.showLanguageToggleButton();

        // artificially click on the fallback language to propagate the correct state to the tab bar
        me.markLanguageItemAsSelected(me.FALLBACK_CODE);
        simulateClickOnSelectedLanguage();
        console.log("on start")
    } else {
        // Set the current tab active
        this._multilineHeader.querySelector('li.mls-language-tab .nav-link').classList.add('active');
    }
    // Set the corresponding tab content active
    me._multilineContent.querySelector('.tab-pane').classList.add('active');

    if (this.languageManagementEnabled) {
        this.showAddLanguageButton();

        const me = this;
        this.forEachValidLanguage(function (langCode) {
            const _additionalLanguageOption = me.buildAddLanguageEntry(langCode);
            _additionalLanguageOption.addEventListener('click', function () {
                me.addLanguage(langCode, true);

                // Remove the active class from all tabs not being active after adding the new language option
                me._multilineHeader.querySelectorAll('li.mls-language-tab').forEach(function (_tab) {
                    if (_tab.dataset.lang !== langCode) {
                        _tab.querySelector('.nav-link').classList.remove('active');
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

                    // artificially click on the new language to propagate the correct state to the tab bar
                    me.markLanguageItemAsSelected(langCode);
                    simulateClickOnSelectedLanguage();
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
    _link.classList.add('text-decoration-none', 'dropdown-item');
    _link.href = '#' + this.fieldName + '-' + langCode;
    _link.dataset.bsToggle = 'tab';

    const _flag = this.renderFlag(langCode);
    _link.appendChild(_flag);
    const _text = document.createTextNode(this.getLanguageName(langCode));
    _link.appendChild(_text);

    const _languageLi = document.createElement('li');
    _languageLi.dataset.lang = langCode;
    _languageLi.appendChild(_link);

    _link.addEventListener('click', () => {
        this.updateLanguageSwitcherLabel(langCode);
        // Mark the element as selected in the list of language entries
        this.markLanguageItemAsSelected(langCode);
    });

    return _languageLi;
}

MultiLanguageField.prototype.buildAddLanguageEntry = function (langCode) {
    const _link = document.createElement('a');
    _link.classList.add('text-decoration-none');

    const _flag = this.renderFlag(langCode);
    _link.appendChild(_flag);
    const _text = document.createTextNode(this.getLanguageName(langCode));
    _link.appendChild(_text);
    const _languageLi = document.createElement('li');
    _languageLi.classList.add('dropdown-item');
    _languageLi.classList.add('cursor-pointer');
    _languageLi.dataset.lang = langCode;
    _languageLi.appendChild(_link);

    return _languageLi;
}

MultiLanguageField.prototype.updateLanguageSwitcherLabel = function (langCode) {
    const _anchor = this._toggleLanguageButton.querySelector('.dropdown-toggle');
    _anchor.textContent = '';

    const _flag = this.renderFlag(langCode);
    _anchor.appendChild(_flag);

    const _name = document.createElement('span');
    _name.textContent = this.getLanguageName(langCode);
    _anchor.appendChild(_name);
}

MultiLanguageField.prototype.markLanguageItemAsSelected = function (langCode) {
    // add custom selection class 'language-selected' as Bootstrap does not allow multiple active elements in the same nav
    this._toggleLanguageOptions.querySelectorAll('ul>li').forEach(function (_li) {
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
                _langOption.classList.add('d-none');
            }
        });
    } else {
        this._modalInputs.querySelectorAll('.row').forEach(function (_row) {
            const lang = _row.querySelector('input').dataset.lang;
            const _langOption = me._addLanguageOptions.querySelector('li[data-lang="' + lang + '"]');
            if (_langOption) {
                _langOption.classList.add('d-none');
            }
        });
    }

    const _selectableOption = me._addLanguageOptions.querySelector('li:not(.d-none)');
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
