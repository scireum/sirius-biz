/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

function MultiLanguageField(options) {
    this.FALLBACK_CODE = 'fallback';

    this.fieldName = options.fieldName;
    this.hasFallback = options.hasFallback || false;
    this.fallbackLabel = options.fallbackLabel;
    this.wrapperId = options.wrapperId;
    this.modalId = options.modalId;
    this.values = options.values;
    this.languageManagementEnabled = options.languageManagementEnabled;

    this.validLanguages = {};
    if (this.hasFallback) {
        this.validLanguages.fallback = this.fallbackLabel;
    }
    for (let langCode in options.validLanguages) {
        this.validLanguages[langCode] = options.validLanguages[langCode];
    }

    this._wrapper = document.getElementById(this.wrapperId);
    this._input = this._wrapper.querySelector('.mls-input');
    this._hiddenInputs = this._wrapper.querySelector('.mls-hidden-inputs');
    this._modal = document.getElementById(this.modalId);

    this._modalInputs = this._modal.querySelector('.mls-modal-inputs');
    this._modalPlaceholder = this._modal.querySelector('.mls-modal-placeholder')

    const me = this;

    $(me._modal).on('hidden.bs.modal', function () {
        me.updateHiddenFields();
    })

    this.renderModalBody();
    this.updateHiddenFields();
    this.updateLanguageManagementOptions();
}

MultiLanguageField.prototype.renderLanguageRow = function (langCode, langName) {
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
    _name.textContent = langName;
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
MultiLanguageField.prototype.renderModalBody = function () {
    this._modal.classList.add('mls-modal');
    
    let rowsAdded = false;
    for (let langCode in this.validLanguages) {
        let langName = this.validLanguages[langCode];

        if (!this.languageManagementEnabled || langCode === this.FALLBACK_CODE || this.values[langCode]) {
            let _row = this.renderLanguageRow(langCode, langName);
            this._modalInputs.appendChild(_row);
            rowsAdded = true;
        }
    }

    if (this.languageManagementEnabled) {
        if (!rowsAdded) {
            this._modalPlaceholder.classList.remove('hidden');
        }

        const _addLanguageButton = this._modal.querySelector('.mls-add-language-button');
        _addLanguageButton.classList.remove('hidden');

        const _addLanguageOptions = _addLanguageButton.querySelector('.dropdown-menu');

        for (let langCode in this.validLanguages) {
            let _language = document.createElement('li');
            _language.classList.add('pointer');
            _language.dataset.lang = langCode;
            let _link = document.createElement('a');
            _link.textContent = this.validLanguages[langCode];

            let me = this;
            _link.addEventListener('click', function () {
                let _row = me.renderLanguageRow(langCode, me.validLanguages[langCode]);
                me._modalInputs.appendChild(_row);
                me._modalPlaceholder.classList.add('hidden');
                me.updateLanguageManagementOptions();
            });
            _language.appendChild(_link);
            _addLanguageOptions.appendChild(_language);
        }
    }
}

MultiLanguageField.prototype.updateHiddenFields = function () {
    this._hiddenInputs.textContent = '';

    const me = this;
    this._modal.querySelectorAll('input[data-lang]').forEach(function (input) {
        let lang = input.dataset.lang;

        let _hiddenInput = document.createElement('input');
        _hiddenInput.type = 'hidden';
        _hiddenInput.value = input.value;
        _hiddenInput.name = me.fieldName;

        if (me.hasFallback && lang === me.FALLBACK_CODE) {
            // also update main field
            me._input.value = input.value;
        } else {
            _hiddenInput.name += '-' + lang;

        }

        me._hiddenInputs.appendChild(_hiddenInput);
    })
}

MultiLanguageField.prototype.updateLanguageManagementOptions = function () {
    if (!this.languageManagementEnabled) {
        return;
    }

    const _addLanguageButton = this._modal.querySelector('.mls-add-language-button');

    const _addLanguageOptions = _addLanguageButton.querySelector('.dropdown-menu');

    this._modalInputs.querySelectorAll('.row').forEach(function (_row) {
        let lang = _row.querySelector('input').dataset.lang;
        let _langOption = _addLanguageOptions.querySelector('li[data-lang="' + lang + '"]');
        if (_langOption) {
            _langOption.classList.add('hidden');
        }
    });

    const _selectableOption = _addLanguageOptions.querySelector('li:not(.hidden)');
    if (!_selectableOption) {
        _addLanguageButton.classList.add('hidden');
    }
}

MultiLanguageField.prototype.getLanguageName = function (langCode) {
    return this.validLanguages[langCode];
}

MultiLanguageField.prototype.renderFlag = function (langCode) {
    if (langCode === this.FALLBACK_CODE) {
        // globe icon
        const _globe = document.createElement('span');
        _globe.classList.add('mls-language-flag');
        _globe.classList.add('mls-language-globe');
        _globe.textContent = String.fromCodePoint(127758);
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
